# AI 측 작업 요청 — TTS 피드백 분류·송신

마지막 업데이트: 2026-05-26 (gRPC 통일 결정 반영)
대상: **ai-server 담당자**
배경: [`../decisions/tts-design.md`](../decisions/tts-design.md) — 분기 1·2·3·7·8 결정 (2026-05-25). 8-A (단말 OS TTS) MVP 채택. AI 가 8종 enum 분류 + 세션 종료/세트 경계 batch 송신 담당.

> **2026-05-26 추가 결정**: 피드백 batch 송신을 **REST → gRPC 단일화**. 기존 `POST /internal/feedback/batch` 폐기, gRPC `ReportFeedbackBatch` 로 통일. AI→Spring 콜백 채널이 이미 두 가지 gRPC (`SavePoseDataBatch`, `CompleteAnalysis`) 라 일관성·인증 채널 통일을 위해. **proto 양쪽 갱신·Spring 측 핸들러·REST 잔재 삭제는 이미 완료**. AI 담당자는 *pb2 재생성 + spring_client 함수 신설* 만 진행하면 됨.

---

## 0. 작업 패키지 요약

| 항목 | 값 |
|------|---|
| AI 측 코드 변경량 | ~60줄 (BT-SET 포함, httpx→gRPC 로 줄어듬) |
| 추정 시간 | 4~6h (분류 함수 튜닝 제외) |
| 우선순위 | 🔴 시연 직결 — TTS 피드백 전체가 이 작업에 의존 |
| 영향 (AI) | pb2 재생성, `squat_analyzer` 분류 로직, `session_state` 세트 카운터, `spring_client.report_feedback_batch` |
| 선행 의존 | **완료**: Spring BE-13 + 2026-05-26 gRPC 통일 작업 (proto 갱신·핸들러·REST 삭제). AI 측 즉시 시작 가능 |
| 통신 컨벤션 | **gRPC unary** (기존 `report_complete_analysis` 와 동일 패턴) |

---

## 1. 왜 필요한가

1. 요구사항 §6 이 8종 enum (`KNEE_OUT`, `KNEE_IN`, `HIP_LOW`, `HIP_HIGH`, `BACK_BENT`, `SHOULDER_TILT`, `ELBOW_BENT`, `HEAD_DOWN`) 분류를 강제하나, 현재 `squat_analyzer._summarize_rep` 는 자유 문자열만 송신 (`"자세 양호" | "자세 보정 필요"`)
2. 요구사항 §5 가 피드백 이벤트 batch 송신을 AI 책임으로 명시 (실시간 호출 금지, 세션 종료 시점). 송신 채널은 gRPC `ReportFeedbackBatch` 단일 (2026-05-26 통일)
3. 분기 1 의 1-B (AI 분류) + 분기 2 의 2-A (AI 송신) + 분기 7 의 7-1 (HTTP response 확장) 결정 — 모두 AI 측 변경 필요

---

## 2. 코드 변경 (AI 담당자)

### A. proto **갱신 완료 — pb2 재생성만 필요** ✅

**proto 파일 자체는 Spring 측에서 양쪽 동기 갱신 완료** (`ai-server/app/proto/exercise.proto` + `backend/src/main/proto/exercise.proto`).

추가된 RPC + 메시지:

```proto
service ExerciseService {
  // ... 기존
  rpc ReportFeedbackBatch (FeedbackBatchRequest) returns (FeedbackBatchResponse);
}

message FeedbackEvent {
  string feedback_type = 1;                    // 8종 enum 중 하나
  double sync_rate_at_trigger = 2;
  google.protobuf.Timestamp occurred_at = 3;
}

message FeedbackBatchRequest {
  int64 session_id = 1;
  int32 set_no = 2;
  bool is_final = 3;
  repeated FeedbackEvent events = 4;
}

message FeedbackBatchResponse {
  int64 session_id = 1;
  int32 saved_count = 2;
}
```

**AI 담당자 작업**: pb2 재생성

```bash
cd ai-server
python -m grpc_tools.protoc \
  -I app/proto \
  --python_out=. \
  --grpc_python_out=. \
  app/proto/exercise.proto
# 또는 기존 코드가 두 경로 (`./exercise_pb2.py` + `app/proto/exercise_pb2.py`) 를 사용중이라면 둘 다 갱신
```

생성 후 import 경로 확인 (현재 코드는 `import exercise_pb2` 형태 — root level).

### B. `PoseResponse.feedback_type` 신설 (분기 7-1) — 변경 없음

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

### C. 스쿼트 4종 분류 함수 신설 (★) — 변경 없음

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
    if angles.min_hip_angle > 100:
        candidates.append("HIP_HIGH")
    if angles.max_back_angle > 30:
        candidates.append("BACK_BENT")

    if not candidates:
        return None  # 정상 — GOOD_FORM 발화 안 함 (분기 6: 6-A)
    return min(candidates, key=PRIORITY.get)
```

priority 는 `mysql/data.sql:130-134` 의 seed 데이터와 정합.

### D. 판정 이벤트 누적 (세션 메모리) — 변경 없음

**위치**: `ai-server/app/grpc/session_state.py`

```python
class SessionState:
    # ... 기존 필드
    feedback_events: list = []  # 신규

    def append_feedback(self, feedback_type: str, sync_rate: float, occurred_at: datetime):
        self.feedback_events.append({
            "feedback_type": feedback_type,
            "sync_rate_at_trigger": sync_rate,
            "occurred_at": occurred_at,
        })
```

rep 완료 + `classify_rep` 결과가 None 이 아닐 때 누적.

### E. set-boundary batch + 휴식 retry (gRPC 통일 반영) (★)

**분기 2.A.BT (BT-SET) 채택**: 세트 경계마다 mini-batch 송신 + 세션 종료 시 final batch.

**기존 (REST + httpx + asyncio) → 갱신 (gRPC + 동기 + threading)**:

기존 `spring_client.py` 의 `report_complete_analysis` 가 이미 *동기 gRPC + threading.Thread* 패턴이라, BT-SET 도 동일 패턴으로 통일하는 게 일관성 ↑. asyncio 무관.

**위치**: `ai-server/app/grpc/spring_client.py` — 함수 신설

```python
from google.protobuf.timestamp_pb2 import Timestamp

# BT-SET retry: 휴식 시간(30~90초) 안에 거의 100% 성공 보장
_FEEDBACK_RETRY_DELAYS = (0.0, 5.0, 15.0, 35.0)  # 총 worst-case 55초

def report_feedback_batch(
    session_id: int,
    set_no: int,
    is_final: bool,
    events: list[dict],
) -> None:
    """BT-SET 모드: 세트 경계 또는 세션 종료 시 호출.

    events 각 원소 = {"feedback_type": str, "sync_rate_at_trigger": float, "occurred_at": datetime}
    """
    if not events and not is_final:
        return  # 빈 set + 비-final 은 skip

    # proto FeedbackEvent 변환
    proto_events = []
    for e in events:
        ts = Timestamp()
        ts.FromDatetime(e["occurred_at"])  # datetime → google Timestamp
        proto_events.append(exercise_pb2.FeedbackEvent(
            feedback_type=e["feedback_type"],
            sync_rate_at_trigger=e["sync_rate_at_trigger"],
            occurred_at=ts,
        ))

    request = exercise_pb2.FeedbackBatchRequest(
        session_id=session_id,
        set_no=set_no,
        is_final=is_final,
        events=proto_events,
    )

    last_error = None
    for attempt, delay in enumerate(_FEEDBACK_RETRY_DELAYS, start=1):
        if delay > 0:
            time.sleep(delay)
        try:
            response = get_stub().ReportFeedbackBatch(request, metadata=auth_metadata())
            logger.info(
                "[AI → Spring] ReportFeedbackBatch 성공 (session=%s, set_no=%s, is_final=%s, saved=%s, attempt=%d)",
                session_id, set_no, is_final, response.saved_count, attempt,
            )
            return
        except grpc.RpcError as e:
            last_error = e
            logger.warning(
                "[AI → Spring] ReportFeedbackBatch 실패 (session=%s, set_no=%s, attempt=%d): %s",
                session_id, set_no, attempt, e.details(),
            )
    logger.error(
        "[AI → Spring] ReportFeedbackBatch 최종 실패 (session=%s, set_no=%s): %s",
        session_id, set_no, last_error.details() if last_error else "unknown",
    )
    # 최종 실패 시 events_buffer 유지 — 다음 set/final batch 시 함께 송신할지는 호출 측 정책
```

**호출 측 (session_state.py)**:

```python
# threading 기반 — 운동 메인 루프 블로킹 회피
import threading

class SessionState:
    target_reps_per_set: int       # 12-persona-difficulty.md 의 targetReps 공식 계산
    current_set_reps: int = 0
    current_set_no: int = 1
    events_buffer: list = []

    def on_rep_completed(self, event):
        if event.get("feedback_type"):
            self.events_buffer.append(event)
        self.current_set_reps += 1

        if self.current_set_reps >= self.target_reps_per_set:
            buffer_snapshot = list(self.events_buffer)
            set_no = self.current_set_no
            self.events_buffer = []
            self.current_set_reps = 0
            self.current_set_no += 1

            # 휴식 시간 백그라운드 송신
            threading.Thread(
                target=spring_client.report_feedback_batch,
                args=(self.session_id, set_no, False, buffer_snapshot),
                daemon=True,
            ).start()

    def on_session_end(self):
        buffer_snapshot = list(self.events_buffer)
        self.events_buffer = []
        threading.Thread(
            target=spring_client.report_feedback_batch,
            args=(self.session_id, self.current_set_no, True, buffer_snapshot),
            daemon=True,
        ).start()
```

**손실 시나리오 종합 (BT-SET, gRPC)**:
| 시나리오 | 동작 | 손실 |
|---|---|---|
| 정상 운동 종료 | 세트마다 batch + final | 0 |
| 세트 중간 강제 종료 | 진행 중 set 의 events 손실 | max 1 set |
| 휴식 중 강제 종료 | 직전 set batch 는 retry 로 송신 완료 | 0 |
| 네트워크 일시 단절 | 휴식 시간 retry 로 복구 | 0 (휴식 30~90s 내) |
| Spring 5xx 일시 | gRPC retry 로 복구 | 0 |
| AI 크래시·재시작 | 메모리 손실 | 전체 (옵션 C 디스크 결합 시 0) |

### F. 세션 종료 신호 수신 (분기 2.A.ET → **✅ ET-H 확정 2026-05-26**)

**최종 결정 (2026-05-26)**: 클라는 Spring 의 `PATCH /sessions/{id}/end` **한 번만** 호출. Spring 이 afterCommit 으로 AI 에 gRPC `StopAnalysis` 송신. AI 측 코드는 **기존 `StopAnalysis` 핸들러 그대로 사용** — 신규 endpoint 불필요. 분석 문서: [`../decisions/session-end-trigger.md`](../decisions/session-end-trigger.md).

**AI 담당자 작업**: 기존 `StopAnalysis` 핸들러 (`ai-server/app/grpc/exercise_servicer.py:98`) 안에 `state.on_session_end()` 호출 **1줄 추가**.

```python
def StopAnalysis(self, request, context):
    session_id = request.session_id
    state = get_registry().remove(session_id)
    if state is None:
        return exercise_pb2.StopResponse(success=False, ...)

    # 신규: BT-SET final batch (별도 스레드, on_rep_completed 와 동일 패턴)
    state.on_session_end()

    # 기존: CompleteAnalysis 콜백 스레드
    threading.Thread(target=_send_complete_analysis, args=(state,), daemon=True).start()

    return exercise_pb2.StopResponse(success=True, ...)
```

**중요**: ET-H 확정으로 *클라가 AI 에 직접 HTTP 호출하는 endpoint 신설 불필요*. 이전 handoff 의 "옵션 1/2" 분기 무효화.

---

## 3. 협의 안건 정리 (참고, 2026-05-26 갱신)

`tts-design.md` §12.2 에 상세. 요약:

| 안건 | 합의 대상 | 상태 |
|---|---|---|
| ~~`POST /internal/feedback/batch` payload schema~~ → **proto `FeedbackBatchRequest` 정의** | Spring 담당자 | ✅ 완료 (2026-05-26 gRPC 통일) |
| proto `feedback_type` 8종 enum 표기 (UPPER_SNAKE) | Spring + Front 3자 | 🟢 박힘 (proto string + 화이트리스트) |
| 임계값·priority 위치 | AI 단독 (공유 권장) | 🟡 |
| 종료 신호 형식 (ET-A vs ET-B) | Front 담당자 | 🔴 코드와 doc 불일치 |
| 재시도·멱등성·부분 실패 | Spring 담당자 | 🟢 INSERT IGNORE + uniqueKey 완료 |
| 시간대·시간 형식 (Asia/Seoul) | 3자 | 🟢 박힘 |
| 내부 토큰 관리 (gRPC `Authorization: Bearer`) | Spring 담당자 | 🟢 박힘 (`InternalAuthInterceptor`) |

---

## 4. 의식적으로 *안 해야 하는* 것

| 안 해야 할 것 | 이유 |
|---|---|
| 8종 enum 중 스쿼트와 무관한 4종 (`HIP_LOW`, `SHOULDER_TILT`, `ELBOW_BENT`, `HEAD_DOWN`) 분류 | [[project-squat-first]] — 추후 운동 추가 시 |
| `GOOD_FORM` 송신 | 요구사항 §6 8종 enum 에 없음. 분기 6-A |
| 운동 중 매 rep 즉시 batch 송신 (BT-REP) | 분기 2.A.BT 거부. Spring 자원 비효율 + retry 예산 부족 |
| LLM 호출로 분류 | 분기 1 의 1-B 결정. LLM 은 분류에 부적합 |
| TTS 합성·발화 결정 | 클라+OS 책임 (분기 8: 8-A) |
| Spring 의 `session_feedback_logs` 직접 insert | Spring 책임. AI 는 gRPC `ReportFeedbackBatch` 만 |
| REST `POST /internal/feedback/batch` 호출 | **폐기됨** (2026-05-26). gRPC 만 |

---

## 5. 검증

| 항목 | 측정 |
|---|---|
| 8종 분류 정확도 | 사용자 영상 5건 → 사람 판정 vs AI 분류 일치도 (스쿼트 활성 4종 한정) |
| rep 단위 1발화 보장 | 한 rep 안에서 `feedback_type` 송신 개수 ≤ 1 |
| batch 누락 | 세션 종료 시 `session_feedback_logs` 행 수 == AI 측 누적 카운터 (멱등 흡수 감안) |
| gRPC retry 동작 | Spring 강제 5xx 응답 시 4회 attempt 로그 확인 |
| 정상 rep 처리 | 결함 없을 시 `feedback_type=null` 응답, batch 미포함 |

---

## 6. 관련 문서

- [`../decisions/tts-design.md`](../decisions/tts-design.md) — TTS 전체 설계 (분기 1·2·3·7·8 결정)
- [`../REQUIREMENTS.md`](../REQUIREMENTS.md) §5·6·8 — 요구사항 근거
- [`../tasks/23-ai-tasks-detail.md`](../tasks/23-ai-tasks-detail.md) — AI 작업 항목
- [`./ai-h2-auth-middleware.md`](./ai-h2-auth-middleware.md) — 선행 작업 (H2 인증 미들웨어, 별도 패키지)
- `backend/src/main/java/com/shadowfit/service/Exercise/ExerciseGrpcService.java:104` — gRPC `reportFeedbackBatch` 핸들러 (참고용)
- `backend/src/main/java/com/shadowfit/service/Exercise/FeedbackLogService.java` — proto 직접 수신·INSERT IGNORE 멱등성
