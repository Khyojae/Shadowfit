# AI 측 작업 요청 — TTS 피드백 분류·송신

마지막 업데이트: 2026-05-25
대상: **ai-server 담당자**
배경: [`../decisions/tts-design.md`](../decisions/tts-design.md) — 분기 1·2·3·7·8 결정 (2026-05-25). 8-A (단말 OS TTS) MVP 채택. AI 가 8종 enum 분류 + 세션 종료 시 batch POST 담당.

---

## 0. 작업 패키지 요약

| 항목 | 값 |
|------|---|
| 코드 변경량 | ~70~100줄 (AI 측만) |
| 추정 시간 | 4~6h (분류 함수 튜닝 제외) |
| 우선순위 | 🔴 시연 직결 — TTS 피드백 전체가 이 작업에 의존 |
| 영향 | `PoseResponse` 신규 필드, `squat_analyzer` 분류 로직, `spring_client` batch 메서드, proto 1 필드 |
| 선행 의존 | Spring 측 `POST /internal/feedback/batch` endpoint 신설 |

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

### E. 세션 종료 시 batch POST (★)

**위치**: `ai-server/app/services/spring_client.py`

```python
async def report_feedback_batch(self, session_id: int, events: list[FeedbackEvent]):
    """세션 종료 시 1회 호출. 분기 2-A."""
    payload = {
        "sessionId": session_id,
        "events": [
            {
                "feedbackType": e.feedback_type,
                "syncRateAtTrigger": e.sync_rate_at_trigger,
                "occurredAt": e.occurred_at.isoformat(),
            }
            for e in events
        ],
    }
    headers = {"X-Internal-Token": settings.SPRING_INTERNAL_TOKEN}
    async with httpx.AsyncClient(timeout=10.0) as client:
        resp = await client.post(
            f"{settings.SPRING_BASE_URL}/internal/feedback/batch",
            json=payload,
            headers=headers,
        )
        resp.raise_for_status()
```

**협의 필요 (Spring 담당자)**:
- payload 필드명 camelCase vs snake_case (위는 camelCase 가정)
- 타임아웃 (위는 10초)
- 재시도 정책 — 5xx 시 backoff retry 3회? 현재는 1회만
- 멱등성 — 같은 sessionId 재송신 처리. Spring upsert 가정
- 부분 실패 — events 일부 invalid 시 응답 정책

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