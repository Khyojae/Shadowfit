# AI 측 작업 요청 — TTS 피드백 분류·송신

마지막 업데이트: 2026-05-25
대상: **ai-server 담당자**
배경: [`../decisions/tts-design.md`](../decisions/tts-design.md) — 분기 1·2·3·7·8 결정 (2026-05-25). 8-A (단말 OS TTS) MVP 채택. AI 가 8종 enum 분류 + 세션 종료 시 batch POST 담당.

---

## 0. 작업 패키지 요약

| 항목 | 값 |
|------|---|
| 코드 변경량 | ~100~130줄 (AI 측만, BT-SET 포함) |
| 추정 시간 | 5~7h (분류 함수 튜닝 제외) |
| 우선순위 | 🔴 시연 직결 — TTS 피드백 전체가 이 작업에 의존 |
| 영향 | `PoseResponse` 신규 필드, `squat_analyzer` 분류 로직, `session_state` 세트 카운터, `spring_client` batch 메서드, proto 1 필드 |
| 선행 의존 | Spring 측 BE-13 (페르소나 분기 + BT-SET DTO 확장 + 멱등성 uniqueKey). **Spring BE-13 완료 후 AI 작업 시작 권장** |
| 통신 컨벤션 | snake_case (협의 안건 #3 결정) |

---

## 1. 왜 필요한가

1. 요구사항 §6 이 8종 enum (`KNEE_OUT`, `KNEE_IN`, `HIP_LOW`, `HIP_HIGH`, `BACK_BENT`, `SHOULDER_TILT`, `ELBOW_BENT`, `HEAD_DOWN`) 분류를 강제하나, 현재 `squat_analyzer._summarize_rep` 는 자유 문자열만 송신 (`"자세 양호" | "자세 보정 필요"`)
2. 요구사항 §5 가 `POST /internal/feedback/batch` 송신을 AI 책임으로 명시 (실시간 호출 금지, 세션 종료 시 1회)
3. 분기 1 의 1-B (AI 분류) + 분기 2 의 2-A (AI 송신) + 분기 7 의 7-1 (HTTP response 확장) 결정 — 모두 AI 측 변경 필요

---

## 2. 코드 변경 (AI 담당자)

### A. proto 확장 — `RepCompletedEvent.feedback_type` (★)

**위치**: `ai-server/app/proto/exercise.proto` + `backend/src/main/proto/exercise.proto` (양쪽 동기)

```proto
message RepCompletedEvent {
  // ... 기존 필드 유지
  string feedback_type = N;   // 신규. 정상 rep 은 빈 문자열
}
```

**협의 필요**: 필드 번호 N — Spring 담당자와 협의. string vs enum (현재 추천: string + 8종 화이트리스트 검증)

### B. `PoseResponse.feedback_type` 신설 (분기 7-1)

**위치**: `ai-server/app/models/pose.py`

```python
class PoseResponse(BaseModel):
    success: bool
    # ... 기존 필드
    rep_completed: bool | None = None
    sync_rate: float | None = None
    feedback_type: str | None = None    # ← 신규. 정상이면 None
```

`ai-server/app/api/endpoints/pose.py` 의 rep 완성 분기에서 `feedback_type` 채워서 응답.

### C. 스쿼트 4종 분류 함수 신설 (★)

**위치**: `ai-server/app/core/squat_analyzer.py`

```python
PRIORITY = {
    "BACK_BENT": 5,
    "KNEE_OUT": 10,
    "KNEE_IN": 20,
    "HIP_HIGH": 30,
}

def classify_rep(landmarks, angles) -> str | None:
    """rep 단위 결함 분류. 다중 검출 시 priority 최솟값 1개 반환.

    스쿼트 한정 4종. 나머지 4종(HIP_LOW, SHOULDER_TILT, ELBOW_BENT, HEAD_DOWN)은
    스쿼트와 무관하여 미분류 (project-squat-first).
    HIP_LOW(엉덩이 처짐)는 플랭크 전용. 스쿼트의 "충분히 못 내려감"은 HIP_HIGH.
    """
    candidates = []

    # 임계값은 영상 5~10건 튜닝으로 조정 필요
    if angles.knee_distance_ratio > 1.2:
        candidates.append("KNEE_OUT")
    if angles.knee_distance_ratio < 0.8:
        candidates.append("KNEE_IN")
    if angles.min_hip_angle > 100:  # 엉덩이가 충분히 안 내려감 → HIP_HIGH
        candidates.append("HIP_HIGH")
    if angles.max_back_angle > 30:
        candidates.append("BACK_BENT")

    if not candidates:
        return None  # 정상 — GOOD_FORM 발화 안 함 (분기 6: 6-A)
    return min(candidates, key=PRIORITY.get)
```

priority 는 `mysql/data.sql:130-134` 의 seed 데이터와 정합:
- BACK_BENT: 5 (가장 우선)
- KNEE_OUT: 10
- KNEE_IN: 20
- HIP_HIGH: 30

**협의 필요**:
- 임계값 (1.2, 0.8, 100, 30) — *AI 단독 결정* 이나 Spring·Front 와 공유 시 디버깅 용이. 영상 5~10건으로 튜닝
- priority 상수 위치 — AI 내장 (3-A-1, 추천) vs Spring 부팅 시 fetch (3-A-2)

### D. 판정 이벤트 누적 (세션 메모리)

**위치**: `ai-server/app/core/session_state.py`

```python
class SessionState:
    # ... 기존 필드
    feedback_events: list[FeedbackEvent] = []  # 신규

    def append_feedback(self, feedback_type: str, sync_rate: float, occurred_at: datetime):
        self.feedback_events.append(FeedbackEvent(
            feedback_type=feedback_type,
            sync_rate_at_trigger=sync_rate,
            occurred_at=occurred_at,
        ))
```

rep 완료 + classify 결과가 None 이 아닐 때 누적.

### E. set-boundary batch + 휴식 retry (★)

**분기 2.A.BT (BT-SET) 채택**: 세트 경계마다 mini-batch 송신 + 세션 종료 시 final batch. 매 rep 송신 (BT-REP) 거부 — Spring 자원 비효율 + retry 예산 부족.

**`target_reps_per_set` 수신 방법**:
- `Member.selectedPersona` + `Session.difficultyLevel` 로 *공식 계산*:
  ```python
  # 12-persona-difficulty.md 의 getDifficultyConfig 와 동일 공식
  def compute_target_reps(persona: str, level: int) -> int:
      base = 5 if persona == "REHAB" else 10
      return base + (level - 1) * 2
  ```
- AI 가 세션 시작 시 (`POST /pose` 의 첫 호출 또는 별도 setup 신호) `persona`, `difficulty_level` 받음
- *AI 측 자체 계산* 권장 — Spring 무수정. 공식이 안정적이라 drift 위험 낮음
- 또는 Spring 이 `Session` 응답에 `target_reps_per_set` 컬럼 추가 — Spring 변경 +1줄

**위치**: `ai-server/app/services/spring_client.py`, `ai-server/app/core/session_state.py`

```python
# session_state.py
class SessionState:
    target_reps_per_set: int       # 세션 시작 시 받음 (12-persona-difficulty.md targetReps)
    current_set_reps: int = 0
    current_set_no: int = 1
    events_buffer: list = []

    async def on_rep_completed(self, event):
        if event.feedback_type:
            self.events_buffer.append(event)
        self.current_set_reps += 1

        if self.current_set_reps >= self.target_reps_per_set:
            asyncio.create_task(self.send_set_batch(is_final=False))   # 휴식 중 백그라운드
            self.current_set_reps = 0
            self.current_set_no += 1

    async def on_session_end(self):
        await self.send_set_batch(is_final=True)   # 잔여분 + final 플래그

    async def send_set_batch(self, is_final: bool):
        if not self.events_buffer and not is_final:
            return   # 빈 batch 는 skip (set 안에 결함 없으면)

        payload = {
            "session_id": self.session_id,
            "set_no": self.current_set_no,
            "is_final": is_final,
            "events": [
                {
                    "feedback_type": e.feedback_type,
                    "sync_rate_at_trigger": e.sync_rate_at_trigger,
                    "occurred_at": e.occurred_at.isoformat(),
                }
                for e in self.events_buffer
            ],
        }
        # 휴식 시간 활용 retry: 0s, 5s, 15s, 35s backoff (총 ~55s, 최소 휴식 30s 내 거의 성공)
        for delay in [0, 5, 15, 35]:
            await asyncio.sleep(delay)
            try:
                await self.spring_client.report_feedback_batch(payload)
                self.events_buffer = []   # 성공 시 버퍼 비움
                return
            except (httpx.TimeoutException, httpx.HTTPStatusError) as e:
                logger.warning(f"batch send retry {delay}s: {e}")
        # 3회 실패 — events_buffer 유지 → 다음 set batch 시 함께 송신 (또는 final batch 시)
        logger.error(f"batch send failed after retries: set_no={self.current_set_no}")
```

```python
# spring_client.py
async def report_feedback_batch(self, payload: dict):
    """BT-SET 모드: 세트 경계 또는 세션 종료 시 호출."""
    headers = {"X-Internal-Token": settings.SPRING_INTERNAL_TOKEN}
    async with httpx.AsyncClient(timeout=10.0) as client:
        resp = await client.post(
            f"{settings.SPRING_BASE_URL}/internal/feedback/batch",
            json=payload,
            headers=headers,
        )
        resp.raise_for_status()
# snake_case 채택. Spring DTO @JsonNaming(SnakeCaseStrategy.class) 전제.
```

**payload schema** (snake_case, 협의 안건 #3):
```json
{
  "session_id": 123,
  "set_no": 2,
  "is_final": false,
  "events": [
    { "feedback_type": "KNEE_OUT", "sync_rate_at_trigger": 52.3, "occurred_at": "..." }
  ]
}
```

**협의 필요 (Spring 담당자)**:
- payload — **snake_case** + 신규 필드 `set_no`, `is_final` (협의 안건 #3 갱신)
- 타임아웃 10초 + AI 측 3회 retry (총 ~55s 안에 결정)
- **멱등성** (협의 안건 #10) — `session_feedback_logs` 에 `(session_id, occurred_at, feedback_type)` uniqueKey 추가 + `INSERT IGNORE` 또는 `ON DUPLICATE KEY UPDATE`. 같은 events 재송신 안전 흡수
- 부분 실패 — events 일부 invalid 시 응답 정책 (현재 추정: 유효한 것만 insert, reject 목록 응답)
- 빈 batch (`events=[]`) 처리 — `is_final=true` 일 때만 허용 vs 항상 허용

**손실 시나리오 종합**:
| 시나리오 | BT-SET 동작 | 손실 |
|---|---|---|
| 정상 운동 종료 | 세트마다 batch + final | 0 |
| 세트 중간 강제 종료 | 진행 중 set 의 events 손실 | max 1 set |
| 휴식 중 강제 종료 | 직전 set batch 는 retry 로 송신 완료 | 0 |
| 네트워크 일시 단절 | 휴식 시간 retry 로 복구 | 0 (휴식 30~90s 내) |
| Spring 5xx 일시 | 휴식 시간 retry 로 복구 | 0 |
| AI 크래시·재시작 | 메모리 손실 | 전체 (옵션 C 디스크 결합 시 0) |

→ 운영 진입 시 디스크 영속화 (옵션 C) 와 결합하면 *모든 시나리오 손실 0* 가능.

### F. 세션 종료 신호 수신 (분기 2.A.ET 의 ET-A)

**위치**: `ai-server/app/api/endpoints/pose.py` 또는 신규 `sessions.py`

**옵션 1**: 마지막 `POST /pose` 의 `session_end=true` 플래그
```python
@router.post("/pose")
async def pose(req: PoseRequest):
    # ... 기존 처리
    if req.session_end:
        await trigger_batch_send(req.session_id)
```

**옵션 2**: 별도 endpoint
```python
@router.post("/sessions/{session_id}/end")
async def end_session(session_id: int):
    await trigger_batch_send(session_id)
```

**협의 필요 (Front 담당자)**:
- 옵션 1 vs 2 선택
- batch 송신 후 메모리 정리 시점
- 종료 신호 누락 시 timeout safety net (분기에선 거부, 안전망 도입 가능성)

---

## 3. 협의 안건 정리 (참고)

`tts-design.md` §12.2 에 상세. 요약:

| 안건 | 합의 대상 | 우선순위 |
|---|---|---|
| `POST /internal/feedback/batch` payload schema | Spring 담당자 | 🔴 |
| proto `feedback_type` 필드 번호·타입 | Spring 담당자 | 🔴 |
| 8종 enum 표기 (UPPER_SNAKE) | Spring + Front 3자 | 🔴 |
| 임계값·priority 위치 | AI 단독 (공유 권장) | 🟡 |
| 종료 신호 형식 (옵션 1 vs 2) | Front 담당자 | 🟡 |
| 재시도·멱등성·부분 실패 | Spring 담당자 | 🟡 |
| 시간대·시간 형식 (ISO 8601 + tz) | 3자 | 🟢 |
| 내부 토큰 관리 | Spring 담당자 | 🟢 |

---

## 4. 의식적으로 *안 해야 하는* 것

| 안 해야 할 것 | 이유 |
|---|---|
| 8종 enum 중 스쿼트와 무관한 4종 (`HIP_HIGH`, `SHOULDER_TILT`, `ELBOW_BENT`, `HEAD_DOWN`) 분류 | [[project-squat-first]] — 추후 운동 추가 시 |
| `GOOD_FORM` 송신 | 요구사항 §6 8종 enum 에 없음. 분기 6-A |
| 운동 중 실시간 batch 송신 | 요구사항 §5 — 세션 종료 시 1회만 |
| LLM 호출로 분류 | 분기 1 의 1-B 결정. LLM 은 분류에 부적합 |
| TTS 합성·발화 결정 | 클라+OS 책임 (분기 8: 8-A) |
| Spring 의 `session_feedback_logs` 직접 insert | Spring 책임. AI 는 batch POST 만 |

---

## 5. 검증

| 항목 | 측정 |
|---|---|
| 8종 분류 정확도 | 사용자 영상 5건 → 사람 판정 vs AI 분류 일치도 (스쿼트 활성 4종 한정) |
| rep 단위 1발화 보장 | 한 rep 안에서 `feedback_type` 송신 개수 ≤ 1 |
| batch 누락 | 세션 종료 시 `session_feedback_logs` 행 수 == AI 측 누적 카운터 |
| 정상 rep 처리 | 결함 없을 시 `feedback_type=null` 응답, batch 미포함 |

---

## 6. 관련 문서

- [`../decisions/tts-design.md`](../decisions/tts-design.md) — TTS 전체 설계 (분기 1·2·3·7·8 결정)
- [`../REQUIREMENTS.md`](../REQUIREMENTS.md) §5·6·8 — 요구사항 근거
- [`../tasks/23-ai-tasks-detail.md`](../tasks/23-ai-tasks-detail.md) — AI 작업 항목 (이 작업 신설 대상)
- [`./ai-h2-auth-middleware.md`](./ai-h2-auth-middleware.md) — 선행 작업 (H2 인증 미들웨어, 별도 패키지)