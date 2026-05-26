# Decision: TTS 음성 안내 설계

상태: **OPEN (사용자 결정 대기)**
작성: 2026-05-25 · 갱신: 2026-05-26 (gRPC 통일 결정 추가)
배경 문서: [`../REQUIREMENTS.md`](../REQUIREMENTS.md) §5·6·8, [`../11-tts-youtube-guide.md`](../11-tts-youtube-guide.md), [`../05-database-design.md`](../05-database-design.md) (TTS 스키마 섹션)
연관: [`./ai-backend-coupling.md`](./ai-backend-coupling.md), [`../tasks/22-backend-tasks-detail.md`](../tasks/22-backend-tasks-detail.md), [`../tasks/23-ai-tasks-detail.md`](../tasks/23-ai-tasks-detail.md), [`../handoff/ai-tts-feedback-batch.md`](../handoff/ai-tts-feedback-batch.md)

> **✅ 2026-05-26 결정 (송신 채널 통일)**: 피드백 batch 송신을 **REST → gRPC 단일화**.
>
> - 변경: 기존 REST `POST /internal/feedback/batch` (`InternalFeedbackController`) **폐기**.
> - 신규: gRPC `ExerciseService.ReportFeedbackBatch` (FastAPI → Spring) 추가. `FeedbackBatchRequest` / `FeedbackEvent` / `FeedbackBatchResponse` 메시지 정의.
> - 근거: AI→Spring 콜백이 이미 두 가지 gRPC (`SavePoseDataBatch`, `CompleteAnalysis`) — 채널·인증(`Authorization: Bearer`) 일관성 ↑. `X-Internal-Token` REST 인증 어긋남 제거. BT-SET retry 가 동일 채널 재사용. proto schema 강제로 JSON validation 누락 버그 차단.
> - 박힌 코드: `backend/src/main/proto/exercise.proto` + `ai-server/app/proto/exercise.proto` (메시지·RPC 동기), `ExerciseGrpcService.reportFeedbackBatch`, `FeedbackLogService.saveBatch(FeedbackBatchRequest proto)` (D-2: 서비스 레이어 proto 직접).
> - 삭제된 코드: `InternalFeedbackController`, `FeedbackBatchRequestDto`, `FeedbackEventDto`, `application.yml` 의 `/internal/**` whitelist.
> - 본 문서 §2.A.BT, §7, §8, §11 등 본문의 "POST /internal/feedback/batch" 표기는 모두 위 gRPC 경로로 의미 치환하여 읽을 것. 본문 일일이 수정하지 않음 — 작업·결정 맥락 보존을 위해 원형 유지 + 본 박스만 추가.
> - AI 측 작업 가이드: [`../handoff/ai-tts-feedback-batch.md`](../handoff/ai-tts-feedback-batch.md) 참조 (2026-05-26 동시 갱신).

---

## 1. 배경 / 문제

### 1.1 요구사항이 명시하고 있는 것 (REQUIREMENTS.md)

| 항목 | 위치 | 내용 |
|------|------|------|
| 발화 트리거 | §1 정책 + 사용자 구두 확인 (2026-05-25) | **rep 1회 완료 후, 자세 이상 시에만 TTS** — 이상 없으면 발화 없음 |
| `FeedbackType` 8종 enum 고정 | §6 | `KNEE_OUT`, `KNEE_IN`, `HIP_LOW`, `HIP_HIGH`, `BACK_BENT`, `SHOULDER_TILT`, `ELBOW_BENT`, `HEAD_DOWN` |
| 템플릿 속성 | §6 | `(exercise + feedback_type)` 유니크, `priority` 로 **다중 검출 시 우선순위** 결정 |
| 발화 로그 송신 경로 | §5 | `POST /internal/feedback/batch` — **FastAPI 가 세션 종료 시 한 번에 전송 (실시간 호출 금지)** |
| TTS 설정 | §1·8 | `ttsEnabled` (default true), `ttsSpeed` (0.5~2.0). `GET/PATCH /preferences/tts` |
| 실시간 부하 분리 | §핵심 아키텍처 | 운동 중 발화는 device TTS, 서버 저장은 종료 시 배치 1회 |

### 1.2 현재 코드 실태

- AI `squat_analyzer._summarize_rep` ([`squat_analyzer.py:316-321`](../../ai-server/app/core/squat_analyzer.py)): rep 종료 시 `sync_rate` 만 보고 `"자세 양호" | "자세 보정 필요" | "즉시 자세 수정 필요"` **자유 문자열** 송신. **8종 enum 분류 로직 없음.**
- DB: `exercise_feedback_templates`, `session_feedback_logs` 스키마 존재 ([`05-database-design.md`](../05-database-design.md):159, 174). `feedback_type VARCHAR(30)` 인데 어떤 enum 이 들어갈지 코드에는 미정의.
- proto `RepCompletedEvent`: `feedback_message` 만 있음, `feedback_type` 필드 없음.
- 23-ai-tasks-detail.md: **8종 분류 작업이 아직 작업 항목으로 잡혀 있지 않음.**

### 1.3 제약

- AI 코드 변경 최소화 ([`feedback-minimize-python-changes`](../../../C:/Users/khjae/.claude/projects/E--init/memory/feedback_minimize_python_changes.md)) — 단, 요구사항이 8종 분류·batch 송신을 AI 책임으로 명시하므로 *이번 한 번* 의 AI 변경은 정당화 필요·불가피
- 스쿼트 단일 운동 ([`project-squat-first`](../../../C:/Users/khjae/.claude/projects/E--init/memory/project_squat_first.md)) — 8종 중 스쿼트와 무관한 것(`ELBOW_BENT` 등)은 당장 분류 안 해도 됨
- 한국어 단일 멘트 ([`project-korean-only`](../../../C:/Users/khjae/.claude/projects/E--init/memory/project_korean_only.md))

### 1.4 평가 가치 우선순위 (사용자 명시, 2026-05-25)

이 문서의 모든 분기 추천은 다음 우선순위로 평가:

1. **돈** — 운영 비용 최소. 지속 비용이 발생하는 안은 1회성 비용 또는 0원 안에 비해 강하게 페널티
2. **운동 중 끊기지 않음** — 발화가 네트워크·서비스 장애로 멈추면 안 됨. *오프라인 가능성* 이 사실상 hard requirement
3. **사용자에게 구체적으로 인지** — 멘트가 무엇을 의미하는지 알아들을 수 있어야 함 (자연스러움보다는 *인지 가능성·명료성*)

같은 가치의 안이 여럿이면 상위 가치(1 > 2 > 3) 순으로 우선. 분기 8 (합성 방식) 이 이 우선순위로 직접 영향받음 — 기존 분기 1~7 도 이 기준으로 재점검했고 추천 변경 없음.

---

## 2. 결정해야 할 분기점

요구사항이 일부 항목을 이미 못 박았으므로 (8종 enum, FastAPI batch 송신), 남은 분기는 *요구사항이 닫지 않은 빈틈* 뿐.

- **분기 1.** `feedback_type` 결정 위치 — 요구사항이 8종 enum 을 강제하지만 *누가 분류하는지* 는 미정
- **분기 2.** 발화 로그 ↔ AI 판정 이벤트 의미 정의 — "발화 로그" 인지 "AI 판정 이벤트" 인지
- **분기 3.** rep 단위 발화 정책 — 같은 rep 안에서 다중 결함 동시 검출 시 처리
- **분기 4.** 템플릿 캐시 전략
- **분기 5.** TTS 비활성·실패 시 fallback
- **분기 6.** GOOD_FORM 발화 여부
- **분기 7.** 실시간 피드백 채널 — rep 판정 결과가 클라에 도달하는 데이터 플로우
- **분기 8.** TTS 합성 방식 — 음성을 어떻게 만들고 재생하는가
- **분기 9.** 기준 영상 소리 vs 피드백 TTS 충돌 처리 — audio ducking 정책

---

## 분기 1. `feedback_type` 결정 위치 → 추천 **1-B (AI 가 8종 분류 후 송신)**

요구사항 §6 이 8종 enum 을 못 박았으므로 *enum 자체* 는 결정됨. 남은 질문: 자유 문자열인 현재 AI 출력을 누가 8종으로 변환하는가.

| 옵션 | 의미 | AI 변경 | Spring 변경 | 클라 변경 | 트레이드오프 |
|------|------|--------|------------|---------|------------|
| 1-A. 클라가 룰로 type 결정 | 클라가 MediaPipe landmark 로 8종 분류 | 없음 | 없음 | 분류 로직 ⚠️ | landmark 가 클라·AI 양쪽으로 흐름. 같은 판정 로직 두 곳. 클라 단말마다 성능 편차 |
| **1-B. AI 가 8종 분류 후 송신** ⭐ | `RepCompletedEvent` 에 `feedback_type` 필드 추가, AI 가 분류 | **중간** (proto 1필드 + 분류 로직 신설) | 작음 (DTO 1필드) | type → DB message 매핑 | AI 변경량 큼 — *but* 요구사항이 §5 에서 batch 송신을 AI 책임으로 명시. 사실상 강제됨 |
| 1-C. Spring 이 자유문자열 → type 변환 | Spring 이 키워드·정규식으로 변환 | 없음 | 변환 헬퍼 ⚠️ | 없음 | 표현 바뀔 때마다 깨짐. 8종 분류는 landmark 기반이라 문자열로 안 됨 — 구조적으로 불가능 |
| 1-D. type 없이 message 만 | 8종 enum 폐기 | 없음 | 템플릿 테이블 폐기 | 단순 | **요구사항 §6 위반** — 안 됨 |

**추천 1-B 사유**:
- 요구사항이 8종 enum + batch 송신을 강제 → AI 손대는 것은 *이미 결정된 사항*
- landmark 가 AI 쪽에만 흐르므로 분류 로직도 AI 가 자연스러움 (1-A 는 landmark 를 클라로도 흘려야 함)
- 멘트 변경은 여전히 DB 만으로 가능

**AI 변경 비용 (현실 추정)**:
- proto: `RepCompletedEvent` 에 `string feedback_type = 5;` 1줄
- `squat_analyzer`: 8종 중 스쿼트에 의미 있는 것(`KNEE_OUT`, `KNEE_IN`, `HIP_HIGH`, `BACK_BENT`)을 landmark 기반으로 분류하는 함수 신설. 한 결함당 임계값 1~2개 (예: `KNEE_OUT` = 무릎이 발끝보다 X cm 바깥). **`HIP_HIGH` 채택 사유**: 스쿼트에서 "충분히 못 내려감" 결함은 *엉덩이가 들려 있는* 상태이므로 `HIP_HIGH`(엉덩이 과도하게 들림)가 정합. seed 데이터 `data.sql:130-134` 와도 일치 (`HIP_HIGH` 사용)
- 23-ai-tasks-detail.md 에 **새 작업 항목** 으로 추가 필요 (현재 누락)

**감수 트레이드오프**:
- AI 코드 변경 *발생* — [[feedback-minimize-python-changes]] 와 마찰. 단 요구사항이 AI 책임으로 명시했으므로 정당화 가능
- 8종 enum 늘어날 때마다 AI 코드 수정 (런지·플랭크 도입 시 `ELBOW_BENT` 등 활성화). 단 스쿼트 단일 운동 기간 동안은 변동 없음
- 스쿼트와 무관한 4종(`ELBOW_BENT`, `HEAD_DOWN`, `SHOULDER_TILT`, `HIP_LOW`) 은 당장 분류 안 함 — DB 템플릿도 *스쿼트에 해당하는 것만* 등록. `HIP_LOW`(엉덩이 처짐)는 플랭크에서 활성 (`data.sql:145`)

---

## 분기 2. 발화 로그 ↔ AI 판정 이벤트 의미 정의 → 추천 **2-A (판정 이벤트 = 송신 이벤트)**

요구사항 §5 가 "FastAPI 가 세션 종료 시 한 번에 전송"으로 송신 경로를 못 박았으므로 *경로* 는 결정됨. 남은 질문: 클라가 실제 발화했는지 여부를 AI 가 알아야 하나.

| 옵션 | 의미 | 트레이드오프 |
|------|------|------------|
| **2-A. AI 판정 이벤트 = 송신 이벤트** ⭐ | AI 가 rep 종료 시 분류한 결과를 자기가 누적 → 세션 종료 시 batch POST. **클라의 실제 발화 여부 무관** | 단순. AI 단독으로 batch 완결. `ttsEnabled=false` 여도 판정 이벤트는 기록됨 — 분석·리포트용 가치 큼 |
| 2-B. 실제 발화한 것만 로그 | 클라가 발화 결과를 AI 에 다시 송신, AI 가 발화한 것만 batch | hop 1개 추가. `ttsEnabled=false` 사용자는 데이터 없음 → 리포트에서 결함 패턴 분석 불가 |
| 2-C. Spring 직송 + AI 송신 둘 다 | 양쪽에서 받음 | 중복·순서 문제 |

**추천 2-A 사유**:
- "발화 로그" 라는 가이드 표기를 **"AI 판정 이벤트 로그" 로 의미 재정의** 하는 게 자연스러움 — 요구사항의 의도는 *세션의 자세 결함 분포 기록* 이지 *스피커가 울렸는가* 가 아님
- TTS off 사용자도 리포트에서 자기 결함 패턴 볼 수 있음 (가치 큼)
- 송신 주체가 AI 하나로 일관 — 클라 → AI 역방향 채널 불필요

**감수 트레이드오프**:
- `session_feedback_logs` 테이블·컬럼명을 "발화" 어감에서 떼야 함 — 단 컬럼 자체(`feedback_type`, `sync_rate_at_trigger`, `occurred_at`) 는 의미 그대로
- `11-tts-youtube-guide.md` 의 "발화 이벤트 로그" 표기 → "피드백 판정 이벤트 로그" 로 정정

**batch payload (AI → Spring)**:
```json
POST /internal/feedback/batch
Header: X-Internal-Token: ***
{
  "session_id": 123,
  "events": [
    { "feedback_type": "KNEE_OUT", "sync_rate_at_trigger": 52.3, "occurred_at": "2026-05-25T10:23:45+09:00" },
    { "feedback_type": "BACK_BENT", "sync_rate_at_trigger": 48.1, "occurred_at": "2026-05-25T10:24:12+09:00" }
  ]
}
```

### 2.A.ET 세션 종료 trigger — *누가 AI 에게 "세션 끝" 을 알리나?* → **✅ ET-H 확정 (2026-05-26 재검토)**

> **✅ 2026-05-26 재검토 결정 (ET-A → ET-H 변경)**: 2026-05-25 ET-A 결정의 전제 (REST batch + AI 가 송신 주체이므로 trigger 도 AI 직접) 가 *gRPC 통일 결정* 으로 무효화됨. 또한 ET-A 의 "Spring 장애 시 AI 가 trigger 받음" 장점이 실제로는 *batch 송신처가 Spring 이라 의미 없음* 으로 드러남.
>
> - **확정**: ET-H (단일 endpoint 분배자 패턴). 클라 → `PATCH /sessions/{id}/end` 단 한 번만 호출. Spring 이 endTime 기록 + afterCommit 으로 AI 에 gRPC StopAnalysis 송신
> - **삭제**: `ExercisesController.stopSession` (`PUT /exercises/sessions/{id}/stop`)
> - **유지**: `ExerciseAnalysisService.stopAnalysis` (호출자 변경 — controller 가 아닌 SessionService afterCommit), AI 측 `StopAnalysis` gRPC 핸들러 (변경 없음)
> - **safety net**: `SessionTimeoutScheduler` 가 IN_PROGRESS → FAILED 자동 정리 (이미 구현됨)
> - **분석 문서**: [`./session-end-trigger.md`](./session-end-trigger.md)

> ~~**결정 (2026-05-25)**: ET-A (클라가 Spring + AI 양쪽에 직접 종료 통보) 채택. BE-14 endpoint (`PATCH /sessions/{id}/end`) 유지.~~ (2026-05-26 무효화)
> **호출 trigger 3가지 인지**:
> - A 사용자 명시 종료 (버튼) — 클라가 endpoint 호출
> - B 목표 rep/세트 달성 자동 종료 — 클라가 자동으로 endpoint 호출 (UI 가 "운동 끝났습니다 ✓" 확인 받고 동일 endpoint)
> - C 강제 종료 (앱 죽음·네트워크 끊김) — endpoint 호출 불가, 별도 **safety net (협의 안건 #26, 베타 진입 전 도입)**
>
> ET-C (AI timeout 자동 종료) 거부 사유 재확인: *세트 사이 휴식 시간(30~90초)도 frame 안 오는 구간이라 휴식과 종료 구분 불가*. ET-B (Spring 경유) 도 hop 추가로 거부.

분기 2 가 *송신 주체는 AI* 라고 결정했지만, AI 가 세션 종료 시점을 어떻게 인지하느냐는 미정. "운동 종료" 버튼 → AI batch POST 사이 trigger 경로.

| 옵션 | 흐름 | 트레이드오프 |
|------|------|------------|
| **ET-A. 클라 → AI 직접 신호** ⭐ | 마지막 `POST /pose` 에 `session_end=true` 플래그, 또는 별도 `POST /sessions/{id}/end` 를 AI 에 직접. 클라는 동시에 `PATCH /sessions/{id}/end` 를 Spring 에도 송신 (종료 시각 기록) | 기존 클라 ↔ AI 채널 활용. 분기 7-1 정합. AI 가 batch 송신 주체이므로 trigger 도 자기가 받는 게 책임 일관 |
| ET-B. 클라 → Spring → AI | `PATCH /sessions/{id}/end` 만 Spring 에 보내고, Spring 이 AI 에 RPC 로 종료 알림 | hop 1개 추가. Spring → AI 단방향 RPC 채널 신규 필요. 요구사항 §1 "실시간 부하 분리" 와는 무관 (세션 종료 시점이라) |
| ET-C. AI 자체 timeout | N초간 `/pose` 미수신 시 AI 자동 종료 | 비결정적. 사용자가 *명시적 종료 누른 직후 batch 즉시 송신* 보장 못 함. 일시 정지 후 재개와 구분 어려움 |

**추천 ET-A 사유**:
- 분기 7-1 (HTTP 응답 확장) 과 같은 정신 — *기존 채널 활용*, 신규 인프라 회피
- 분기 2-A 가 *AI 를 송신 주체* 로 결정했으므로 *종료 trigger 도 AI 가 직접 수신* 이 책임 일관 (Spring 우회 = 분기 7-4 거부 정합)
- 클라가 *Spring 과 AI 양쪽에 종료 통보* — 분리된 두 책임:
  - **Spring**: 세션 row 의 `ended_at` 기록
  - **AI**: 누적된 판정 이벤트 batch 송신

**감수 트레이드오프**:
- 클라가 종료 시 2번 호출 (Spring 1번, AI 1번). 둘 중 하나 실패해도 다른 쪽은 진행 — 부분 실패 허용
- AI batch 가 늦거나 실패하면 Spring DB 에 ended_at 만 있고 feedback_logs 비어 있는 상태 가능 → batch 재시도 큐 (AI 측) 또는 1학기엔 best-effort
- ET-A 흐름:
  ```
  사용자 "운동 종료" 버튼
        │
        ├─ PATCH /sessions/{id}/end  → Spring (종료 시각 기록)
        └─ POST  /sessions/{id}/end  → AI    (batch 송신 trigger)
                                            └─ POST /internal/feedback/batch → Spring
  ```

### 2.A.BT 송신 trigger 시점 — *세션 전체에 1회인가 분할인가?* → 추천 **BT-SET (세트 경계 batch + 세션 종료 final)**

분기 2-A 가 *송신 주체는 AI* 로 결정했으나, *송신 시점* 이 세션 종료 1회만으로 충분한가는 별도 검토 필요. 강제 종료 / AI 크래시 / 네트워크 단절 시 *전체 세션 손실* 위험.

| 옵션 | trigger | 세션당 호출 | 손실 최대 | 요구사항 §5 정합 | 비고 |
|------|------|:--:|:--:|:--:|------|
| BT-NONE | 세션 종료 1회 (현행) | 1 | 전체 세션 | ✅ | 단순. 강제 종료 시 큰 손실 |
| BT-REP | 매 rep 즉시 송신 | ~30 | 0 | ❌ 위반 | Spring 자원 비효율 (commit 30회), retry 예산 부족 |
| BT-5REP | 5 rep 마다 (RC-2 piggyback) | ~6 | 4 rep | 🟡 해석 변경 | trigger 가 인위적 (5의 배수) |
| **BT-SET** ⭐ | **세트 경계 + 세션 종료 final** | **3~5** | **1 set** | 🟡 해석 변경 | **운동 도메인 자연 단위. 휴식 시간 활용** |
| BT-TIME | 30초 간격 timer | ~수~수십 | ~30초 분량 | 🟡 | 분석 SDK 패턴. trigger 인위적 |

**추천 BT-SET 사유**:
- **자연 trigger** — 운동의 *set* 단위가 mainstream (Strava·Apple Fitness·Peloton 모두 활용)
- **휴식 시간 활용** — 세트 끝마다 사용자 30~90초 휴식 (`12-persona-difficulty.md` 의 `restTimeSec`). Spring 호출이 *운동 진행 중 아닌 휴식 중* 발생 → 부담 분산
- **Retry 예산 30~90초** — 휴식 시간에 batch 실패 시 재시도 (5s/15s/35s backoff) 가능. 다음 세트 시작 전 거의 100% 성공
- **호출 효율** — BT-REP 대비 commit 횟수 1/10, BT-NONE 대비 손실 위험 1/n
- **운동 도메인 패턴 일치** — Peloton 인터벌 sync, Strava 세그먼트 sync 와 같은 단위

**감수 트레이드오프**:
- max 1 set (~10 rep) 손실 — 운영 진입 시 *디스크 영속화 (옵션 C)* 결합으로 0 가능
- 요구사항 §5 *문구* ("종료 시 배치 1회") 와 어긋남 → *정신* ("실시간 매 rep 호출 금지") 으로 해석 변경 필요. 본 문서가 그 결정의 박제

**Set 인지 방법**:
```python
# session_state.py
class SessionState:
    target_reps_per_set: int  # session 시작 시 받음 (12-persona-difficulty.md 의 targetReps)
    current_set_reps: int = 0
    current_set_no: int = 1
    events_buffer: list = []

    async def on_rep_completed(self, event):
        if event.feedback_type:
            self.events_buffer.append(event)
        self.current_set_reps += 1

        if self.current_set_reps >= self.target_reps_per_set:
            await self.send_set_batch(is_final=False)
            self.current_set_reps = 0
            self.current_set_no += 1

    async def send_set_batch(self, is_final: bool):
        payload = {
            "session_id": self.session_id,
            "set_no": self.current_set_no,
            "is_final": is_final,
            "events": self.events_buffer,
        }
        # 휴식 시간 활용 retry (5s, 15s, 35s backoff)
        for delay in [0, 5, 15, 35]:
            await asyncio.sleep(delay)
            try:
                await self.spring_client.report_feedback_batch(payload)
                self.events_buffer = []
                return
            except (httpx.TimeoutException, httpx.HTTPStatusError):
                continue
        # 3회 실패 시 events_buffer 유지 → 다음 set batch 시 함께 송신
```

**Spring DTO 확장**:
```java
public record FeedbackBatchRequestDto(
    @NotNull Long sessionId,
    @NotNull Integer setNo,           // ← 신규 (BT-SET)
    @NotNull Boolean isFinal,         // ← 신규 (마지막 batch 인지)
    @NotEmpty List<FeedbackEventDto> events
) {}
```

**멱등성 (협의 안건 #10 동시 결정)**:
- `session_feedback_logs` 에 `(session_id, occurred_at, feedback_type)` uniqueKey 추가
- Spring 측 `INSERT IGNORE` 또는 `ON DUPLICATE KEY UPDATE` 로 중복 흡수
- BT-SET 의 *retry* 가 안전하게 동작 (같은 events 재송신 OK)

**도입 시점**:
- 1학기 MVP 시연 (~5명): BT-NONE 도 OK (시연 controlled). 단 BE-13 시점에 어차피 코드 만지므로 BT-SET 미리 도입도 합리적
- 베타 (50~100명): **BT-SET 도입 권장** ⭐
- 정식 (1000+명): + 디스크 영속화 (옵션 C) 결합 → 손실 0

#### 2.A.BT 책임 분담

| 작업 | 주체 | 위치 | 분량 |
|------|------|------|------|
| DTO `set_no`/`is_final` 필드 + `@JsonNaming` | Spring | `FeedbackBatchRequestDto`, `FeedbackEventDto` | ~5줄 |
| `session_feedback_logs` uniqueKey 변경 + INSERT IGNORE | Spring | schema·`SessionFeedbackLog`·`FeedbackLogService` | ~5줄 |
| set 카운터 + 분기 | AI | `session_state.py` | ~15줄 |
| `send_set_batch` + 휴식 retry (0s/5s/15s/35s) | AI | `session_state.py` + `spring_client.py` | ~40줄 |
| `target_reps_per_set` 공식 계산 (`12-persona-difficulty.md` 와 동일) | AI | `session_state.py` | ~5줄 |
| Front | **변경 없음** — 클라는 발화 채널 (분기 7-1) 만 관여, batch 무관 | — | 0 |

→ **Spring 측 ~10줄** (BE-13 의 작업 B 로 통합) + **AI 측 ~60줄** (handoff `ai-tts-feedback-batch.md` §E)

#### 2.A.BT 점진 전환 (기존 코드와의 호환성)

```
Phase 1 (시연 직전·MVP):
  - Spring: 기존 코드 그대로 (단순 batch endpoint)
  - AI:     BT-NONE 으로 송신 (세션 종료 1회)
  → 호환

Phase 2 (BE-13):
  - Spring: DTO + uniqueKey + INSERT IGNORE 추가 (~10줄)
  - AI:     아직 BT-NONE 유지 가능 (set_no=1, is_final=true 한 번만)
  → 호환 (set_no 미수신 시 null 허용으로 두면)

Phase 3 (베타 진입 전):
  - Spring: 변경 없음 (Phase 2 에서 이미 준비됨)
  - AI:     BT-SET 으로 전환 (set 카운터 + retry)
  → 호환

Phase 4 (정식):
  - AI:     + 디스크 영속화 (옵션 C, SQLite WAL)
  → 손실 0
```

→ Spring 측 작업은 Phase 2 *한 번* 으로 끝. AI 측은 Phase 3 에서 자체 전환.

---

## 분기 3. rep 단위 다중 결함 동시 검출 처리 → 추천 **3-A (priority 최솟값 1개만 송신·발화)**

요구사항이 "rep 1회 = 발화 0~1회" 로 명시하므로 시간 쓰로틀·우선순위 큐는 *대부분 의미 상실*. 남은 진짜 분기는 한 rep 에서 KNEE_OUT + BACK_BENT 가 동시 검출됐을 때.

| 옵션 | 의미 | 트레이드오프 |
|------|------|------------|
| **3-A. priority 최솟값 1개만** ⭐ | DB `priority` 가 가장 낮은(=중요한) type 1개를 AI 가 선택, 그것만 송신. 클라는 받은 1개 발화 | 요구사항 "1회=1발화" 와 정합. `priority` 필드의 진짜 용도가 여기 |
| 3-B. 모두 송신·클라가 선택 | AI 가 검출된 모두 송신, 클라가 priority 로 1개 발화 | priority 가 클라·AI 양쪽 필요. AI 가 이미 DB 와 무관하므로 priority 가져오기 추가 비용 |
| 3-C. 모두 송신·모두 발화 | 다중 발화 | "1회=1발화" 요구사항 위반 |
| 3-D. 가장 먼저 검출된 1개 | 시간순 | "가장 중요한" 이 아닌 "가장 먼저" — 의미 약함. priority 필드 무용지물 |

**추천 3-A 사유**:
- 요구사항 "1회=1발화" 와 직접 정합
- `priority` 필드가 "다중 검출 시 우선순위" 라고 요구사항 §6 이 명시 → 이 분기에서 사용됨
- AI 가 priority 를 알아야 하므로 — *DB 의 priority 가 AI 에 노출 필요* → 두 가지 sub-옵션:

| sub-옵션 | 의미 | 트레이드오프 |
|---------|------|------------|
| **3-A-1. AI 내장 priority 상수** ⭐ | AI 코드에 `PRIORITY = {"KNEE_OUT": 10, "BACK_BENT": 20, ...}` 상수 | DB 와 중복이나 변경 빈도 낮음. AI 가 Spring 호출 불필요 |
| 3-A-2. AI 가 부팅 시 Spring 에서 priority 로드 | AI 가 시작할 때 `GET /exercises/{id}/feedback-templates` 호출 | 한 hop 추가. AI 가 Spring 의존 시작 시점 강함 |

**감수**:
- 3-A-1 은 priority 가 DB·AI 양쪽에 있음 — 단, DB 는 *멘트 매핑용*, AI 는 *결함 선택용* 으로 용도가 다름. 같은 값을 유지하는 책임은 AI 작업자에게.

**폐기되는 정책 (요구사항으로 인해 무의미)**:
- 시간 쓰로틀(3초/5초): rep 간격(평균 2~3초) 이 자연 쓰로틀
- 같은 type 연속 발화 방지 윈도우: rep 마다 새 판정이라 무의미
- HIGH/MEDIUM/LOW 우선순위 큐: 큐 자체 불필요 (1 rep = 0~1 발화)
- LOW 멘트 세트 끝 일괄 발화: LOW 자체 없음

### 3.A.RC rep 카운트 발화 정책 (2026-05-25 사용자 결정 F)

결함 멘트와 별개로 **rep 카운트 발화**(예: `"5회 완료"`) 도 포함. 단 매 rep 카운트는 멘트 폭주라 *마일스톤 단위* 만 발화.

| 옵션 | 동작 | 트레이드오프 |
|------|------|------------|
| RC-1. 매 rep 카운트 발화 | rep 마다 "N회" | 매 2~3초 발화. 결함 멘트와 잦은 충돌. 사용자 멘트 폭주 피로 |
| **RC-2. 5의 배수만 (5/10/15/20…)** ⭐ | 마일스톤 카운트 | 자연스러운 호흡. 화면 UI rep 카운터가 매 회 표시되므로 음성은 마일스톤 충분 |
| RC-3. 카운트 발화 없음 | 화면 UI 만 | 사용자 답 F "있으면 좋지" 와 충돌 |
| RC-4. 세트 종료 시만 "N회 완료" | 세트 단위 | 마일스톤도 없음. 사용자가 운동 중 진행도 인지 어려움 |

**충돌 정책 (결함 멘트 ↔ 카운트 멘트 동시)**:
- 같은 rep 에서 결함 + 카운트 마일스톤 동시 발생 시 → **결함 발화 우선**, 카운트는 *그 발화 끝에 이어 붙임* (예: `"무릎이 발끝을 넘었습니다. 5회"`)
- 카운트만 있고 결함 없으면 → `"5회"` 단독
- 결함만 있고 카운트 마일스톤 아니면 → 결함 멘트만

---

## 분기 4. 템플릿 캐시 전략 → 추천 **4-A (운동 진입 시 1회 메모리 캐시)** (변경 없음)

| 옵션 | 의미 | 트레이드오프 |
|------|------|------------|
| **4-A. 운동 진입 시 1회 로드, 세션 메모리 캐시** ⭐ | 운동 시작 화면에서 `GET /exercises/{id}/feedback-templates` 1회 | 단순. 멘트 변경 다음 세션부터 반영 |
| 4-B. 앱 시작 시 전체 로드 | 모든 운동 템플릿 부팅 시 로드 | 스쿼트 단일 운동에선 4-A 와 동일 |
| 4-C. AsyncStorage 영속 + ETag | If-None-Match | 오버엔지니어링 |

스쿼트 단일 운동 동안 `GET /exercises/1/feedback-templates` 가 사실상 전체 로드.

---

## 분기 5. TTS off · 실패 fallback → 추천 **5-B (자막 + 진동 fallback)** (변경 없음)

| 옵션 | 의미 | 트레이드오프 |
|------|------|------------|
| 5-A. 무음 | `ttsEnabled=false` 면 침묵 | 사용자가 피드백 못 받음 |
| **5-B. 화면 자막 + 진동** ⭐ | 토스트 + `expo-haptics` 짧은 진동 | TTS off 의도(조용한 환경) 호환. 청각 접근성 ↑ |
| 5-C. off 면 자막, 실패면 무음 | 케이스별 다름 | 비일관 |

요구사항 영향 없음. 단 §1 의 "발화 트리거 = rep 1회 후 자세 이상" 은 자막에도 동일 적용 — 자세 이상 시에만 토스트 표시.

**사용자 답변 (2026-05-25)**: 질문 의도 명확화 미완 — 사용자는 "TTS 는 한국어로 해야 하는 거 아니냐" 로 답해 *언어* 와 *off 시 처리* 를 혼동한 것으로 보임. **TTS 가 한국어인 건 확정** ([[project-korean-only]]). **off 시 자막+진동 추천(5-B) 는 아직 미확정** — 다음 검토 시 명시적 confirm 필요.

---

## 분기 6. GOOD_FORM 발화 여부 → 추천 **6-A (발화 안 함)** (요구사항 직접 반영)

| 옵션 | 의미 | 트레이드오프 |
|------|------|------------|
| **6-A. GOOD_FORM 발화·송신 안 함** ⭐ | 자세 이상이 없으면 AI 는 아무 type 도 송신 안 함, 클라는 침묵 | 요구사항 "이상 시" 정합. 가이드의 `"좋은 자세입니다"` 예시 폐기 |
| 6-B. rep 마다 GOOD_FORM 송신·발화 | 매 rep 칭찬 | 요구사항 위반 + 멘트 폭주 |
| 6-C. 송신은 하되 발화 안 함 | 로그용으로 보내고 발화 X | 8종 enum 외 `GOOD_FORM` 별도 추가 필요. 그러나 §6 8종 enum 에 `GOOD_FORM` 없음 — **요구사항이 이미 6-A 채택을 명시** |

**감수**:
- `11-tts-youtube-guide.md:46` 의 `GOOD_FORM` 예시 라인 삭제 필요
- 사용자가 매 rep 잘 하고 있는지 알 수 없음 → 화면 UI 에 rep 카운터·실시간 sync_rate 게이지로 대신 표시 (별도 화면 작업)

**사용자 확정 (2026-05-25)**: B "발화 안 함" 명시. ACCEPTED.

---

## 분기 7. 실시간 피드백 채널 → 추천 **7-1 (HTTP response 확장 — 현행 구조 그대로)**

### 7.0 현재 코드 실태 (결정의 전제)

[`ai-server/app/api/endpoints/pose.py`](../../ai-server/app/api/endpoints/pose.py:42) 확인 결과:

- **Client ↔ AI HTTP 직결 채널이 이미 동작 중** — 매 프레임 `POST /pose` (base64 이미지), rep 완성 시 응답에 `rep_completed=true, sync_rate` 반환
- AI → Spring 채널도 이미 있음 — rep 완성 시 `spring_client.report_pose_data_batch` 로 PoseData 묶음 gRPC 송신
- 즉 **새 채널을 만들지 않아도** feedback_type 을 클라에 전달할 경로가 존재

### 7.1 후보 비교

기호: **C**=Client, **A**=AI(FastAPI), **S**=Spring · `==>` 매 프레임/대량 · `-->` rep/세션 단위 · `~>` 새 인프라

#### 후보 7-1. HTTP response 확장 (현행 구조 그대로) ⭐
```
C ==> A   POST /pose, 매 프레임 (현행)
A --> C   rep 완성 시 응답 body 에 feedback_type 추가          ← 신규 1 필드
A --> S   rep 완성 시 PoseData batch, gRPC (현행)
A --> S   세션 종료 시 POST /internal/feedback/batch           ← 신규 1 메서드
```

#### 후보 7-2. gRPC 양방향 stream
```
C <==> A  gRPC bidi stream (landmarks up, rep event down)     ← 인프라 변경
A --> S   현행 + batch
```

#### 후보 7-3. WebSocket / SSE 별도 채널
```
C ==> A   POST /pose (현행)
A ~> C    WS/SSE rep event push                                ← 새 채널
A --> S   현행 + batch
```

#### 후보 7-4. Spring 경유 (AI → Spring → Client)
```
C ==> A   POST /pose (현행)
A --> S   rep event
S ~> C    SSE / polling                                        ← Spring push 인프라
A --> S   batch
```

#### 후보 7-5. Client 자체 분류 (AI 미경유 realtime)
```
C 자체    landmarks → 8종, 자체 발화
C ==> A   pose data 송신 (분석·리포트용)
A --> S   PoseData + batch
```

### 7.2 비교 표

| 후보 | 발화 latency | AI 변경 | 새 인프라 | Spring 변경 | Client 변경 | 요구사항 §1 "실시간 부하 분리" |
|------|:----:|:----:|:----:|:----:|:----:|:----:|
| **7-1. HTTP 확장** ⭐ | RTT 1회 (현재 동일) | 작음 | 없음 | 작음 (batch endpoint 1) | 1 필드 핸들 | ✅ |
| 7-2. gRPC bidi | 최저 | 큼 (endpoint 재작성) | gRPC client | 작음 | 큼 (lib 추가) | ✅ |
| 7-3. WS/SSE | RTT 1회 | 중간 | WS 채널 | 작음 | WS client | ✅ |
| 7-4. Spring 경유 | RTT 2회 | 작음 | Spring push | 큼 | push client | ❌ Spring 실시간 부담 ↑ |
| 7-5. Client 분류 | 0 | 0 | 없음 | 작음 (batch) | 분류 로직 ⚠️ | ✅ |

### 7.3 추천 7-1 사유

- **현행 채널을 그대로 사용** — Client 가 매 프레임 POST 하면서 응답을 받고 있고, rep 완성 시 응답에 1 필드만 더 끼우면 됨. 분기 1·2 에서 결정한 AI 변경 범위(8종 분류 + batch POST) 를 *넘지 않음*
- 7-2·7-3 의 인프라 추가는 *현재 매 프레임 HTTP 가 동작 중인 시스템에 대한 premature optimization*. latency 가 병목으로 측정되기 전엔 격상 비용 정당화 안 됨
- 7-4 는 요구사항 §1 "운동 중 발화 피드백은 device TTS, 서버 저장은 종료 시 배치 1회" 의 *Spring 실시간 부담 회피* 정신과 정면 충돌
- 7-5 는 분기 1 의 1-B (판정 단일 소스 = AI) 결정과 충돌

### 7.4 감수 트레이드오프

- **매 프레임 HTTP POST 의 비효율은 이번 스코프 밖** — 기존 결정. 배포 후 측정에서 병목이면 후보 7-2/7-3 으로 격상 재검토
- rep 완성 시점이 곧 발화 시점 — 일반적인 rep 길이(~2~3초) 안에서 발화 latency 가 사용자에게 인지되지 않음
- PoseResponse 에 `feedback_type` 필드가 *항상* 포함됨 (정상 rep 은 빈 문자열 또는 null). 클라가 null 체크 1줄로 처리

### 7.5 구현 골격

```python
# ai-server/app/models/pose.py — PoseResponse
class PoseResponse(BaseModel):
    success: bool
    # ... 기존 필드
    rep_completed: bool | None = None
    sync_rate: float | None = None
    feedback_type: str | None = None    # ← 신규. 정상이면 None

# ai-server/app/api/endpoints/pose.py — rep 완성 분기에서
return PoseResponse(
    success=True,
    landmarks=landmarks,
    angles=angles,
    rep_count=state.rep_count,
    rep_completed=True,
    sync_rate=rep_event.sync_rate,
    feedback_type=rep_event.feedback_type,   # ← 신규 (분기 1·3 의 분류 결과)
)
```

```typescript
// Client — POST /pose 응답 핸들러
if (res.rep_completed && res.feedback_type) {
  const message = templateCache[res.feedback_type];   // 분기 4
  if (message) speakOrCaption(message);               // 분기 5
}
```

---

## 분기 8. TTS 합성 방식 → 추천 **8-A (Device TTS `expo-speech`, MVP)** + 격상 트리거 명문화

### 8.0 전제

- 멘트 종류: 8종 결함 enum + rep 카운트 숫자. 동적 합성 부담 **사실상 없음** — 100여개 짧은 클립으로 커버
- 한국어 단일 ([[project-korean-only]])
- 가치 우선순위(§1.4): **돈 > 끊기지 않음 > 인지**

### 후보 4 갈래로 묶어 보기 (큰 그림)

§8.1 의 7개 후보를 *"음성을 누가·언제 만드냐"* 기준으로 묶으면 4 갈래 + 혼합 1개. 상세 비교 전 큰 그림.

#### ① 사전 합성·캐시 — 미리 만들어 두고 운동 중엔 재생만
- **8-B 성우 녹음** — 사람이 스튜디오 녹음 (가장 자연스러움, 30~100만원)
- **8-D 클라우드 TTS 사전 합성** — 외부 API 로 빌드 시 mp3 생성 → CDN (음질 거의 사람, 1회 ~$0.03)
- 공통: latency **0ms** (운동 중 합성 단계 자체 없음, 파일 재생만), 멘트 변경 시 재합성·재배포 필요

#### ② on-device 즉석 합성 — 단말이 직접
- **8-A OS TTS (`expo-speech`)** — OS 내장 한국어 엔진. 한 줄 코드, ~50ms
- **8-E 신경망 모델 번들 (Piper/Sherpa ONNX)** — 앱에 .onnx 박아 단말 CPU 추론. 200~500ms, 앱 +25MB
- 공통: 외부 통신·비용 **0**, 오프라인 OK, 멘트 텍스트만 바꾸면 즉시 반영

#### ③ cloud 실시간 합성 — 발화마다 외부 API
- **8-C 클라우드 TTS 실시간** — rep 마다 OpenAI/Google 호출 → 음성 받아 재생. RTT 300~800ms, 사용자당 **지속 비용**

#### ④ LLM 생성 + TTS — 문장도 매번 새로 만들기
- **8-G LLM + TTS 2단** — LLM 으로 문장 생성 (~1~2초) → TTS 합성 (~500ms) = **총 1~3초**, 2중 지속 비용

#### ⑤ 혼합
- **8-F 하이브리드** — 정적 멘트는 ① 로, 동적 부분(숫자 등) 은 ② 로 처리

#### 한 줄 비교 표

| 갈래 | 합성 주체·시점 | 합성 위치 | latency | 돈 |
|------|--------------|---------|--------|-----|
| ① 사전 캐시 (8-B, 8-D) | 사람/API · **빌드 때 1회** | 외부 → 파일 | 0ms | 1회성 |
| ② on-device (8-A, 8-E) | OS/NN · 발화 때 | 단말 | 50~500ms | 0 |
| ③ cloud 실시간 (8-C) | 외부 API · 발화 때 | 외부 API | 300~800ms | **지속** |
| ④ LLM (8-G) | LLM+TTS · 발화 때 | 외부 API ×2 | 1~3s | **지속 큼** |

§1.4 가치 우선순위 (돈 > 끊김 > 인지) 로 보면 ③·④ 즉시 탈락 → **① 또는 ②** 만 남음. 추천 **8-A (MVP, ② 갈래) + 격상 후보 8-D (① 갈래)** 는 *"② 로 시작, 부족하면 ① 로 격상"* 의 2단 구성.

#### 다른 시각: 2차원 분류 (텍스트 ↔ 음성)

위 ①~⑤ 갈래는 *음성을 누가 만드냐* 한 축으로만 묶은 것. 사실 분기 8 의 7개 후보는 **2차원** 으로 더 정확히 갈라진다 — *텍스트도 누가 만드는가* 가 별도의 축.

```
                  음성(파형) 만드는 곳
                  내부               외부
              ┌──────────────┬──────────────┐
   텍스트      │              │              │
   내부 (DB    │   8-A, 8-E   │   8-B, 8-D   │
   템플릿)     │              │              │
              ├──────────────┼──────────────┤
   텍스트      │              │              │
   외부 (LLM   │   (의미 없음) │    8-G       │
   생성)       │              │              │
              └──────────────┴──────────────┘
```

- **텍스트 내부**: DB `exercise_feedback_templates` 의 고정 한국어 문장. `feedback_type` → 1:1 매핑. *항상 같은 멘트.*
  - 예: `KNEE_OUT` → 항상 `"무릎이 발끝을 넘었습니다"`
- **텍스트 외부**: 발화 시점에 LLM 호출, 컨텍스트(sync_rate, 누적 결함 패턴 등) 기반으로 *그때그때 다른 문장* 생성
  - 예: `KNEE_OUT` 1회차 → `"무릎이 살짝 안쪽으로 들어왔어요"` / 5회차 → `"이번엔 또 안쪽이네요, 발끝 방향 의식해 보세요"`
- **왼쪽 아래 (텍스트 외부 + 음성 내부) 가 빈 이유**: 기술적으로 가능은 하나 *합리성 없음* — LLM 호출 비용은 발생시키면서 음성은 어색한 단말 TTS 로 만들면 가치 1·3 모두 죽음. 비싼 LLM 멘트를 만들었으면 좋은 TTS 로 들려주는 게 일관된 선택 (→ 8-G)

**가치 1 (돈) 영향**:
| 축 | 외부 = 비용 | 내부 = 비용 |
|----|------------|------------|
| 음성 합성 위치 | 지속 또는 1회성 | 0 |
| 텍스트 생성 위치 | **LLM 지속 비용 (보통 TTS 의 5~10배)** | 0 |

LLM 호출 단가가 TTS 단가보다 5~10배 큼 (입출력 토큰 누적). 그래서 8-G (오른쪽 아래) 가 분기 8 중 가장 비싼 안 — *~270만원/월 / 1만명* — 으로 우선 탈락.

**현재 추천 위치**: 8-A (MVP) → 8-D (격상) 모두 **왼쪽 열 (텍스트 내부)**. 오른쪽 열(텍스트 외부, LLM 생성) 은 운동 중 발화에 쓰지 않음 — *세션 종료 후 종합 리포트 (BE-03)* 영역에서 다룸. *"운동 중 발화에는 LLM 미사용"* 이 일관된 결론.

### 8.1 후보 7개 + 음성이 나오는 방법

#### 8-A. Device TTS (`expo-speech`, OS 내장) ⭐

**방식**: *단말 OS 가 텍스트를 받아 그 자리에서 음성으로 합성하는 방식.* 합성기·음성 데이터를 OS 가 자체 제공하므로 외부 통신·번들 파일 모두 0. 한국어 음성 엔진은 iOS/Android 모두 시스템에 내장돼 있음.

1. AI 가 PoseResponse 에 `feedback_type` 송신 (분기 7-1)
2. 클라가 템플릿 캐시(분기 4)에서 `feedback_type → message` 매핑 조회 → `"무릎이 발끝을 넘었습니다"`
3. `Speech.speak(message, { language: 'ko-KR', rate })` 호출
4. `expo-speech` 가 OS native TTS API 호출 (iOS `AVSpeechSynthesizer` / Android `TextToSpeech`)
5. OS 내장 한국어 음성 엔진이 **실시간으로 텍스트 → PCM 합성** → 단말 스피커 출력
- 음성 데이터는 메모리에만 존재. 디스크 저장·외부 통신 0
- **합성 주체: 단말 OS**

#### 8-B. Pre-recorded (성우 녹음)

**방식**: *사람이 미리 녹음한 음성 파일을 골라서 재생하는 방식.* "합성" 단계 자체가 없음 — 모든 멘트는 사전 제작된 .mp3 클립으로 존재하고, 운동 중엔 `feedback_type` 에 매핑된 클립을 단순 재생.

1. **빌드 전 외부 작업** — 성우가 8개 결함 멘트 + 숫자 0~99 녹음 → 편집·노이즈 제거 → .mp3 ~108개
2. 앱 번들에 포함 (또는 첫 실행 시 CDN 다운로드)
3. 운동 중 — 클라가 `feedback_type` → `assets/audio/KNEE_OUT.mp3` 경로 매핑
4. `expo-av` (또는 `react-native-sound`) 로 **.mp3 파일 단순 재생**
- 합성 단계 없음 — 사람이 녹음한 음성을 그대로 재생
- **합성 주체: 사람 (사전)**

#### 8-C. 클라우드 TTS 실시간 (OpenAI / Google / Azure)

**방식**: *발화할 때마다 외부 클라우드 TTS API 에 텍스트를 보내, 그 자리에서 합성된 음성을 받아 재생하는 방식.* 합성 주체는 외부 서비스이며, rep 마다 네트워크 호출이 발생.

1. 클라가 `feedback_type → message` 매핑 조회
2. 클라가 외부 TTS API 호출: `POST https://api.openai.com/v1/audio/speech { "input": message, "voice": "alloy", "format": "mp3" }`
3. API 가 그 자리에서 합성 → audio bytes 응답 (스트리밍 chunk 가능)
4. 클라가 메모리 버퍼로 받아 `expo-av` 재생
- 매번 외부 호출. 결과는 보통 캐시 안 함 (캐시하면 8-D 와 같아짐)
- **합성 주체: 외부 API · 발화 시점**

#### 8-D. 클라우드 TTS + 사전 캐싱

**방식**: *외부 클라우드 TTS 로 가능한 모든 멘트를 빌드 시점에 미리 한 번에 합성해 파일로 만들어 두고, 운동 중엔 그 캐시 파일을 재생만 하는 방식.* 합성 주체는 외부 API 이지만 그 호출 시점이 *발화 시점이 아닌 빌드 시점* 으로 옮겨진 형태. 8-C 의 음질을 가져오면서 8-B 의 재생 안정성·오프라인을 확보.

1. **빌드 시점 1회 — Python 스크립트** 가 8 멘트 + 100 숫자 = ~108개를 OpenAI TTS API 로 합성 → .mp3 저장
2. CDN/S3 업로드 + 매니페스트 JSON 생성: `{ "KNEE_OUT": "https://cdn/.../KNEE_OUT_v1.mp3", ... }`
3. **앱 첫 실행 시** — 매니페스트 다운로드, 모든 .mp3 를 `FileSystem`/`AsyncStorage` 에 저장
4. 운동 중 — `feedback_type` → 캐시된 .mp3 경로 → `expo-av` 재생
- 8-B 와 재생 메커니즘 동일. 차이는 *합성 주체가 외부 API* 라는 점
- **합성 주체: 외부 API · 빌드 시점 (운동 중 0)**

#### 8-E. On-device 신경망 TTS (Piper / Sherpa-ONNX)

**방식**: *단말 안에 신경망 TTS 모델을 직접 넣어, 발화 시점에 그 모델이 단말 CPU 로 음성을 만드는 방식.* 외부 API 도 OS 도 사용하지 않고, 앱이 자체 모델을 들고 합성. 8-A 와 8-C 의 중간 — 오프라인이면서 OS 보다 음질 우위 시도.

1. **빌드 시** — Piper 한국어 모델(.onnx ~25MB + voice config) 을 앱 번들에 포함
2. 앱 시작 시 — ONNX Runtime mobile (`onnxruntime-react-native`) 로 모델 로드 (메모리 ~30MB 점유)
3. 운동 중 — `feedback_type` → message → **phonemizer (한국어 자모 분해) → ONNX 추론 (~200~500ms) → 16kHz PCM 출력**
4. PCM 을 native audio API 로 즉시 재생
- 모든 처리가 단말 CPU. 네트워크·서버 0
- **합성 주체: 단말 NN 모델 · 발화 시점**

#### 8-F. 하이브리드 — 정적 8-D + 동적 8-A

**방식**: *멘트를 두 종류로 나눠, 미리 만들 수 있는 정적 멘트는 8-D 클립으로·매번 달라지는 동적 부분만 8-A OS 합성으로 처리하는 혼합 방식.* 정적 부분의 음질·즉시성을 살리면서 동적 부분의 유연성도 확보.

- 8종 결함 멘트 ("무릎이 발끝을 넘었습니다" 등) → 8-D 흐름 (사전 합성 .mp3 재생)
- "5회 완료" 의 숫자 부분 또는 정형이 아닌 멘트 → 8-A device TTS 동적 합성
- 실제로는 숫자 0~99 도 사전 합성하면 8-D 로 통일 가능 → F 는 *진정으로 동적인 멘트가 있을 때만* 의미

#### 8-G. LLM 생성 + TTS

**방식**: *발화할 문장 자체를 LLM 이 발화 시점에 즉석에서 만들고, 그 문장을 다시 TTS 로 음성화하는 두 단계 방식.* 멘트가 매번 달라지는 게 핵심 가치 — 같은 결함이라도 사용자 상태·누적 패턴에 따라 다른 코칭 문장이 나옴.

1. AI 가 `feedback_type` 송신 (분기 7-1)
2. 클라가 LLM API 호출 — 시스템 프롬프트 ("스쿼트 코치 페르소나, 한 문장 한국어") + 컨텍스트 (sync_rate, 누적 결함 패턴, 이번 결함)
3. LLM 응답 ~1~2초 후 → 텍스트: `"이번엔 무릎이 살짝 안쪽으로 들어왔어요. 발끝과 같은 방향으로!"`
4. 그 텍스트를 다시 TTS API (C 또는 D 단계) 로 합성 → audio
5. 재생
- LLM 호출 + TTS 호출이 직렬. **총 1~3초 지연**
- **합성 주체: LLM (문장) + TTS (음성) · 둘 다 발화 시점**

### 8.2 가치 우선순위 기준 비교 표

| 후보 | 가치 1 돈 | 가치 2 끊기지 않음 | 가치 3 인지 (명료성) | 종합 |
|------|:--:|:--:|:--:|:--:|
| **8-A. Device TTS** ⭐ | **0원** | ✅ 완전 오프라인 | ○ (단말 의존 — iOS 자연스러움, Android 일부 어색하나 *발음·내용 인지는 충분*) | **가치 1·2 압도적 만족, 가치 3 충분** |
| 8-B. Pre-recorded | 녹음 1회 ~30~100만원 | ✅ | ◎ | 가치 1 약점 (초기 비용 큼) |
| 8-C. 클라우드 실시간 | **지속 비용** $4~15/1M chars + RTT ~수백 ms | ❌ 네트워크 장애 시 발화 침묵 | ◎ | **가치 1·2 모두 위배** |
| 8-D. 클라우드 + 캐싱 | 합성 1회 < 1만원 | ✅ (캐시 후) | ◎ | 가치 3 최강. 가치 1 1회성 |
| 8-E. On-device NN | 0원 | ✅ | ○~△ (한국어 OSS 모델 어색함 있음) | 가치 1·2 충족하나 앱 사이즈 +20~50MB |
| 8-F. 하이브리드 | D 와 동일 | ✅ | ◎ | D 보다 구현 복잡 |
| 8-G. LLM + TTS | **지속 비용 큼** (LLM + TTS 2단) | ❌ | ◎ (문장 자유) | **가치 1·2 모두 큰 위배** |

### 8.3 추천 8-A 사유 (가치 우선순위 직접 적용)

- **가치 1 (돈)**: 8-A 와 8-E 만 0원. 다른 모두 1회성 또는 지속 비용 발생
- **가치 2 (끊기지 않음)**: 8-A·B·D·E·F 가 오프라인 가능. **8-C·G 즉시 탈락** (네트워크 의존)
- **가치 3 (인지)**: 사용자 표현은 "구체적으로 인지" 로 *명료성·발음 가능성* 의 의미. *자연스러움(브랜드)* 가 아님. 8-A 의 OS TTS 는 한국어 발음·억양이 명확 — 짧은 명령형 멘트("무릎이 발끝을 넘었습니다") 에선 인지에 충분. 자연스러움이 떨어진다는 약점은 *인지* 기준에선 가치 3 위배 아님

→ 가치 1·2·3 을 모두 만족하면서 **돈 0원** 인 유일한 안이 8-A. 8-E 도 0원이나 가치 3 (한국어 OSS 모델 어색함) 에서 8-A 와 비슷하거나 약함 + 앱 사이즈 부담.

### 8.4 격상 트리거 명문화 (가치 3 이 실측에서 부족할 때)

8-A 의 *유일한 위험* 은 가치 3 (인지) 이 단말별 편차로 실제 사용자에게 *알아듣기 어려움* 으로 작용할 가능성. 이 경우 **8-D 로 격상**.

| 트리거 | 격상 대상 |
|--------|----------|
| 사용자 피드백에 "안 들림" / "뭐라는지 모르겠다" 가 5% 이상 | 8-D |
| 베타 테스터 5명 중 2명 이상 Android 단말에서 발음 불명확 보고 | 8-D |
| 운동 중 주변 소음·음악과 섞여 OS TTS 가 묻힌다는 사용성 이슈 | 8-D + 자막 강화 (분기 5 의 5-B 와 결합) |
| 브랜드 차별화 차원에서 자연스러운 음성 요구 (정식 출시 시) | 8-D 또는 8-B |

8-D 로 격상할 경우 **추가 작업** (가치 1 의 비용 영향):
- OpenAI TTS API 키 발급 (~10분)
- 합성 빌드 스크립트 (Python ~50줄, 1시간)
- CDN/Storage 업로드 (S3 또는 GCS, 한 번)
- 클라 캐시 매니저 (`AsyncStorage` + 클립 다운로드, ~100줄)
- 합성 비용: 8 멘트 + 100 숫자 × 평균 20자 ≈ 2,000자 × $15/1M = **0.03 달러** (1회). 멘트 변경 시 마다 재합성하더라도 무시 가능
- 클라 앱 사이즈 영향: 다운로드 후 캐시이므로 *번들 사이즈 0*. 사용자 단말 캐시 ~5~10MB

격상 비용 자체가 작아서 *MVP 는 8-A 로 시작* 이 가치 1 보호 측면에서 최선. 격상 필요성이 실측되면 1~2일 작업.

### 8.5 감수 트레이드오프

- 8-A 의 한국어 음질이 단말 의존이라 **Android 일부 사용자가 OEM TTS 엔진 차이로 들쭉날쭉할 수 있음**. 8.4 트리거가 발동되기 전까진 감수
- 8-A 의 `expo-speech` 가 일부 단말에서 `rate` 적용을 제한적으로 처리할 수 있음 — 분기 5 의 자막 fallback 으로 보강
- 격상 트리거 측정을 위해 **베타 테스터 단계에서 단말별 음질 피드백 항목** 을 설문에 포함

### 8.6 의식적으로 채택 안 한 옵션 (이 분기 한정)

| 안 한 것 | 이유 |
|---------|------|
| 8-B 성우 녹음 부터 시작 | 가치 1 (초기 비용 30~100만원) 위배. 정식 출시 후 브랜드 자산 차원에서 재검토 |
| 8-C 채택 | 가치 1 위배 (지속 비용, 1만명 기준 ~120만원/월) + 운동 중 외부 API 추가 장애 지점 |
| 8-G LLM 채택 | 가치 1 큰 위배 — **저가 모델 가정 시 1만명 기준 ~270만원/월**, 고급 모델은 10배. 가치(멘트 다양성)가 운동 중 사용자에게 체감 약함. LLM 의 합리적 사용처는 *세션 종료 후 종합 리포트* (BE-03 영역) — 분기 8 이 아님 |
| 8-E 신경망 모델 번들 | 가치 1 (0원) 동률이나 가치 3 에서 8-A 보다 명확 우위 없음 + 앱 사이즈 부담 |
| 8-F 하이브리드를 MVP 부터 | 8-D 격상 트리거가 없는 단계에서 미리 짤 이유 없음 — premature |

### 8.7 차원별 deep-dive (결정 근거 보충)

§8.2 의 가치 1·2·3 비교 표를 더 세부 차원 7개로 분해. 각 차원에서 후보들의 위치·이유를 풀어 정리.

#### A) 음질 (한국어 자연스러움)
순위: **8-B ≈ 8-D ≈ 8-C ≈ 8-G ◎  >  8-E ○~◎  >  8-A △**

- 8-A 의 가장 큰 약점. iOS 한국어 TTS 는 Apple Siri 음성으로 자연스럽지만 **Android 는 OEM 마다 엔진이 다름** (삼성·LG·Pixel 등). 일부 단말은 끊어 읽기·억양·외래어 발음이 어색. *단 짧은 명령형 문장의 내용 인지에는 충분*
- 8-B 는 사람 직접 녹음 — 감정·강세 컨트롤 가능, 가장 자연스러움. *코치 페르소나* 강조 시 최고
- 8-C·D 의 클라우드 TTS HD 음성은 사람과 거의 구분 안 됨. **C 와 D 의 음질은 동일** — 차이는 *언제 합성하느냐*
- 8-E 의 OSS NN 모델은 한국어 학습 데이터 한계로 *부자연스러운 억양·간헐적 발음 오류*. 8-A 보다는 낫지만 클라우드와 격차 큼
- 8-G 는 TTS 단계가 D 와 동일하니 음질 동등 + *문장 자유로움* 가치

#### B) 발화 latency (rep 완료 ~ 소리 나오기)
순위: **8-B = 8-D = 8-F(정적) 0ms  >  8-A ~50ms  >  8-E ~200~500ms  >  8-C ~300~800ms  >  8-G 1~3s**

- 8-B·D·F: 사전 합성·로컬 .mp3 재생 → **거의 0ms**
- 8-A: OS 가 텍스트 받자마자 합성 시작, 한국어는 ~50ms. *충분히 즉시적*
- 8-E: 단말 CPU NN 추론. 워밍업 후에도 200~500ms — 살짝 늦은 느낌
- 8-C: API RTT + 합성 + 다운로드 + 재생. **운동 중 사용자에게 인지될 정도의 지연**
- 8-G: LLM(500ms~2s) + TTS(300~800ms) 합산 1~3초. *rep 끝나고 3초 뒤 발화 = 다음 동작과 겹쳐 의미 상실*

**왜 중요한가**: rep 길이 ~2~3초. 발화가 1초 넘게 늦으면 *이미 다음 동작 중* 이라 피드백 무의미

#### C) 비용 (가치 1 직결)
순위: **8-A = 8-E 0원  <  8-D 1회성 < $1  <  8-B 30~100만원(1회)  <  8-C 지속비  <  8-G 지속비 (가장 큼)**

- 8-A·E: 사용자당 0원. OS·번들 모델 처리, 외부 호출 없음
- 8-D: ~108개 클립 합성 ~$0.03 (1회). 멘트 변경 시 재합성도 동일. **사실상 0**
- 8-B: 전문 성우 30~100만원 + 스튜디오. *초기 비용 큼*
- 8-C: rep 마다 외부 API. 7 rep/세션 × 30세션/월 × $0.00045 = $0.094/월/사용자. **1만명 = ~120만원/월**
- 8-G: rep 마다 LLM + TTS 2단. **1만명 = ~270만원/월** (저가 모델 가정. 고급 모델 10배)

**핵심**: 가치 1 하에 **8-A·D 만 살아남음**. C·G 는 사용자 수 선형 증가

#### D) 오프라인 가능성 (가치 2 — 사용자 정정으로 의미 약화)
순위: **8-A·B·D·E·F ✅  >  8-C·G ❌**

- 8-A·B·E: 단말·번들 자원만 사용. 완전 오프라인
- 8-D·F: 첫 실행 클립 다운로드 후 완전 오프라인 (캐시)
- 8-C·G: 운동 중에도 외부 API 호출 필수
- **사용자 정정 (2026-05-25)**: AI 서버 통신이 이미 매 프레임 네트워크라 *TTS 만 오프라인* 의 가치가 약함. 다만 C·G 는 *외부 서비스 (OpenAI 등) 추가 장애 지점* 이라는 약점은 남음 — AI 서버 가용성과 별개

#### E) 멘트 변경 비용 (운영 유연성)
순위: **8-A·C·E·G 0  <  8-D 합성+CDN 재업로드 ~30분  <  8-B 재녹음+재배포 (가장 무거움)**

- 8-A·C·E·G: DB 의 멘트 텍스트 수정만으로 즉시 반영. 앱 재배포 불필요
- 8-D: 합성 스크립트 재실행 (5분) + CDN 재업로드 (5분) + 매니페스트 버전 bump. 사용자 단말은 다음 운동 시 새 클립 자동 다운로드
- 8-B: 스튜디오 재섭외·재녹음·편집·앱 배포. *멘트 한 줄 바꾸려고 통째로*

**왜 중요한가**: 운동 멘트는 사용자 피드백 보고 *문구 다듬기* 가 자주 발생 ("발끝을 넘었습니다" → "발 앞으로 나갔어요"). B 만 무리

#### F) 앱 사이즈
순위: **8-A·C·G 0  <  8-D·F +5~10MB (다운로드 후 캐시, 번들 0)  <  8-B +5~20MB (번들 시)  <  8-E +20~50MB**

- 8-A·C·G: 음성 관련 자원 0
- 8-D·F: 클립이 CDN, 사용자 캐시. *앱 설치 사이즈에 미포함*
- 8-B: 번들 포함 시 클립 수만큼 증가. CDN 방식이면 D 와 동일
- 8-E: NN 모델이 가장 무거움. Piper 한국어 ~25MB. **앱 사이즈 25%+ 증가** — 신규 사용자 다운로드 이탈률 영향

#### G) 구현 비용 (개발 일정)
순위: **8-A 0  <  8-B 클립 매핑 작음  <  8-D·F 중간 (~1~2일)  <  8-C 중간  <  8-E 큼 (RN+ONNX)  <  8-G 가장 큼**

- 8-A: `expo-speech` 한 줄. **0일**
- 8-B: 클립 → feedback_type 매핑 + `expo-av` 재생. 코드는 단순하나 *녹음 워크플로 외주 일정* 별개
- 8-D: 합성 빌드 스크립트(~50줄) + S3/GCS 업로드 + 클라 캐시 매니저(~100줄). **1~2일**
- 8-F: D + "숫자 동적 분기" 로직
- 8-C: API 호출 + 응답 스트리밍 + 실패 fallback
- 8-E: RN 에서 ONNX Runtime mobile 통합. iOS·Android 별 빌드 분기. *까다로움*
- 8-G: LLM API + 프롬프트 엔지니어링 + 응답 안전성 (invalid output 방어) + LLM-TTS 체이닝

**왜 중요한가**: 1학기 MVP 마무리 단계 ([[project-two-semester-schedule]]) 라 *1~2일 추가* 가 한계

#### 가치 우선순위와 차원 매핑

| 가치 | 직결 차원 | 결정에 미치는 영향 |
|------|----------|------------------|
| **1. 돈** | C(비용) + G(구현비용) | 8-C·G 탈락의 결정타. 8-B 도 초기 비용으로 탈락 |
| **2. 끊기지 않음** | D(오프라인) — *사용자 정정 후 약화* | 8-C·G 의 *추가* 외부 장애 지점 약점만 남음 |
| **3. 인지** | A(음질) + B(latency) | 8-A 의 유일한 약점이 음질. 단 단말 편차이지 인지 자체는 충분 |

가치 1·2·3 모두 통과하면서 가치 1 에서 0 원인 안이 **8-A** 한 곳뿐, 가치 1 거의 0 원이면서 가치 3 우위가 **8-D** — 그래서 추천이 **8-A (MVP) → 8-D (격상 시)** 두 단계로 정리됨.

### 8.8 UX 비교 (OS TTS vs MP3) — 박제 2026-05-26

§8.7 까지가 *합성 방식 선택* 의 결정 근거였다면, 본 § 은 *최종 사용자가 어떻게 느끼는가* 관점에서 OS TTS (8-A) 와 MP3 (8-B/8-D) 를 비교하여 결정을 보강. **결론은 변하지 않음 (8-A 유지)**, 단 *시연용 첫인상* 과 *실사용 30분* 의 평가가 정반대로 갈라지는 이유를 박제.

#### 8.8.1 차원별 비교

| UX 차원 | OS TTS (8-A) | MP3 (8-B 성우 / 8-D 클라우드 TTS-HD) | 승자 |
|---|---|---|:--:|
| **첫인상 음질·자연스러움** | 다소 기계적 (특히 Android) | 사람급 | 🏆 MP3 |
| **응답성 (rep 종료 → 발화)** | 50~150ms | 0ms (재생만) | 무승부 (인지 한계 200ms 아래) |
| **속도 조절 슬라이더 사용감** | 어느 값이든 매끄러움 (native 합성 단계 처리) | 0.5x·2.0x artifact, 또는 이산값 강등 | 🏆 OS TTS |
| **발화 일관성** | 매번 미세 차이 (실측 무의미) | 100% 동일 파형 | 🏆 MP3 (미미) |
| **페르소나 음색 차별화** | 단일 voice — pitch/rate 약 조정 + 문장 표현 | 페르소나별 다른 voice 가능 | 🏆 MP3 |
| **오프라인 / 네트워크 안정** | 항상 OK | 번들=OK, CDN=다운로드 의존 | 무승부 (번들 가정) |
| **앱 설치·첫 실행 경험** | 추가 다운로드 0 | +9~40MB 앱 용량 / CDN 첫 다운로드 대기 | 🏆 OS TTS |
| **장시간 사용 단조로움** | 매번 새 합성 — 심리적 덜 반복 | 같은 파형 반복 (30 rep 중 KNEE_OUT 6번 = 완전 동일) | 🏆 OS TTS |
| **접근성 (voiceover 사용자)** | 시각보조 엔진과 동일 voice — 친숙 | 다른 voice — 이질감 | 🏆 OS TTS |
| **배터리·기기 부담** | 합성 연산 ~10ms | 디코딩 ~0 | 무승부 |
| **인지 실패 방지 (자세 교정 직결)** | 자음 살아있음 (native 합성) | stretch artifact 로 자음 뭉개짐 (`ttsSpeed=1.5+`) | 🏆 OS TTS |

#### 8.8.2 시연 5초 vs 운동 30분 — 평가 반전

**시연 자리 (캡스톤 발표)**:
> "와, 사람이 말하는 것 같다" — 첫인상 MP3 압승

**실제 운동 30분 세션**:
- 스쿼트 30 rep × 결함률 20% ≈ 같은 `KNEE_OUT` 멘트 **6번** 들음
- MP3: *완전히 똑같은 파형 6번* → "어, 또 똑같네" 인지 → **자동화된 느낌 ↑, 코치 느낌 ↓**
- OS TTS: 매번 새 합성 (실제론 거의 동일하나 *심리적으로* 덜 반복적)

→ **시연용**은 MP3, **실사용**은 OS TTS 가 UX 우위.

#### 8.8.3 운동 코칭 도메인 특수성

다른 앱(번역기·뉴스 리더기)과의 차이:

| 항목 | 일반 앱 | 운동 앱 (ShadowFit) |
|---|---|---|
| 발화 빈도 | 사용자 트리거 (수동) | rep 마다 자동 (분당 10~20회) |
| 멘트 다양성 | 입력 텍스트 다양 | 8 결함 enum 한정 |
| 사용 길이 | 단발성 | 30분~1시간 지속 |
| 집중 상태 | 화면 응시 | **운동 중** (멘트는 보조) |
| 멘트 인지 실패 비용 | "다시 들으면 됨" | **자세 교정 실패 → 부상 위험** |

→ **반복 빈도 + 지속 시간 + 인지 실패 비용** 세 항목 때문에 *MP3 의 동일 파형 반복이 더 두드러짐*. 일반 앱이면 무시 가능한 단점이 운동 앱에선 부각.

#### 8.8.4 인지 실패 시나리오 (가치 3 직격)

`ttsSpeed=1.5` 사용자가 푸시업 도중 `KNEE_OUT` 들었을 때:

| 방식 | 결과물 | 인지 |
|---|---|---|
| **OS TTS** | 합성 단계에서 1.5배 빠르게 합성 → "무릎 벌리세요" 자음 살아있음 | ✅ |
| **MP3 + pitch-preserve** | 1.0x 파형 stretch → "무릎 벌리세O" 자음 뭉개짐 | ❌ → 자세 교정 실패 → 부상 위험 |

→ 단순 *불편함* 이 아니라 *서비스 핵심 가치(자세 교정) 실패*. MP3 가 가치 3 "인지" 를 *극단 속도 사용자에 한해* 위협.

#### 8.8.5 가중 점수 (운동 도메인)

| 차원 | 가중치 | OS TTS | MP3 |
|---|:--:|:--:|:--:|
| 음질·자연스러움 | 15% | 7 | 10 |
| 응답성 | 5% | 9 | 10 |
| 속도 조절 사용감 | 20% | **10** | 5 |
| 페르소나 차별화 | 10% | 6 | 9 |
| 오프라인 | 15% | 10 | 8 (CDN) / 10 (번들) |
| 첫 실행 경험 | 10% | 10 | 6 |
| 장시간 단조로움 | 15% | 8 | 5 |
| 인지 실패 방지 | 10% | **10** | 6 |
| **가중 점수** | 100% | **8.85** | **7.25** |

→ MP3 가 첫인상 우위에도 *운동 도메인 특수성* 으로 종합 UX 는 OS TTS 우위.

#### 8.8.6 OS TTS 의 페르소나 톤 차별화 가능성

8-A 의 약점인 "페르소나 음색 차별화" 는 *완전한 0* 이 아님. `expo-speech` 옵션으로 부분 보완 가능:

```typescript
Speech.speak(message, {
  language: 'ko-KR',
  pitch: 1.0,       // 0.0 ~ 2.0 (음정)
  rate: ttsSpeed,   // 0.0 ~ 2.0 (속도)
  voice: 'com.apple.voice.compact.ko-KR.Yuna',  // 단말 voice 선택
});
```

**방법**:
1. **단말별 다른 voice 선택**: `Speech.getAvailableVoicesAsync()` 로 ko-KR voice 조회 → 페르소나별 매핑. iOS 16+ 는 Sora(남성·Premium) 등 다양
2. **pitch/rate 미세 조정**: 같은 voice 라도 어감 차이. ±0.15 까지가 자연스러움 한계

**페르소나 톤 매핑 예시 (참고)**:
| 페르소나 | iOS voice | Android voice (예시) | pitch | rate 보정 |
|---|---|---|:--:|:--:|
| BEGINNER (격려·여성톤) | `Yuna (compact)` | `ko-kr-x-koc-local` | 1.05 | ×0.95 |
| ADVANCED (단호·남성톤) | `Sora (premium, iOS 16+)` | `ko-kr-x-koc-network` | 0.95 | ×1.10 |
| DIET (활기) | `Yuna + 조정` | (단말 voice + 조정) | 1.10 | ×1.05 |
| REHAB (안정) | `Yuna + 천천히` | (단말 voice + 천천히) | 1.00 | ×0.85 |

**한계 (감수)**:
- 단말별 사용 가능 voice 다름 → 페르소나 → voice 매핑이 OS 분기 필요
- iOS Premium voice 는 사용자가 별도 다운로드 — 미설치 시 fallback 처리
- pitch ±0.15 한계 — 그 이상은 만화 음성
- MP3 (성우 4명 녹음) 대비 차별화 폭 좁음

→ **MVP 단계 권장**: *문장 자체 차별화 + 약한 pitch/rate 조정* (DB 멘트 페르소나 분기 + Client 측 pitch/rate 분기). 진짜 voice 분리가 필요하면 8-D 격상.

#### 8.8.7 하이브리드 옵션 (8-F) — 비추천

진짜 UX 만 잡고 싶다면 *결함 멘트는 MP3 + rep 카운트는 OS TTS* 혼합. 단:
- 같은 발화 안에서 voice 가 바뀜 ("무릎 벌리세요(사람 음성). 5회(기계음).") → **음색 단절** 이 도리어 부자연스러움
- 두 시스템 lifecycle 관리 부담 (audio session·queue·중단 처리 등)

→ 일관성 문제로 *MVP 단계 비추천*. 운영 단계에서도 자주 채택 안 됨.

#### 8.8.8 검증 권장 (시연 전)

본 결정의 *체감 차이* 가 의심되면 실제 사용자 3~5명으로 검증:

1. 동일 멘트로 OS TTS / MP3 각각 30분 운동 시연
2. 종료 후 NPS + 자유 의견 수집
3. *5초 평가* vs *30분 후 평가* 차이 측정 — 본 § 8.8.2 의 반전 가설 확인

→ 결정이 약하다 싶으면 데이터로 박제. 이미 OS TTS 채택했으니 *기각 사유 보강 자료* 로도 활용.

#### 8.8.9 변동 트리거

다음이 *동시에* 성립하면 8-D (MP3 사전 합성) 로 격상 재검토:

- 멘트 ≥3개월 동안 변경 없음 (멘트 동결)
- 사용자 NPS 에서 "OS TTS 기계적" 불만 누적 (운영 측정)
- 운동 종류·페르소나 확장 멈춤 (재합성 비용 안정)
- `ttsSpeed` 슬라이더 제거 또는 단일 속도 고정 (MP3 의 속도 약점 해소)

→ MVP·베타 단계는 한 가지도 성립 안 함. **현 단계 8-A 유지**.

---

## 분기 9. 기준 영상 소리 vs 피드백 TTS 충돌 처리 → 추천 **9-1 (Audio ducking, `expo-av`)**

### 9.0 배경

사용자 우려 (2026-05-25): *YouTube 기준 영상을 보면서 운동* 하는 모드에서 영상 BGM·코치 멘트와 피드백 TTS 발화가 겹치면 둘 다 안 들림. 가이드 `11-tts-youtube-guide.md` 의 `react-native-youtube-iframe` 시나리오에서 발생.

### 9.1 후보 5개

| 후보 | 동작 | 운동 흐름 | 피드백 인지 | 기준 영상 청취 | 구현 |
|------|------|---------|-----------|------------|------|
| **9-1. Audio ducking** ⭐ | 피드백 발화 동안 기준 영상 볼륨 자동 ↓ (~30%), 발화 종료 시 ↑. `expo-av` `setAudioModeAsync({ interruptionModeAndroid: 'duckOthers', interruptionModeIOS: 'duckOthers' })` | 끊김 없음 | ◎ | ○ (작아짐) | 1줄 설정 |
| 9-2. 영상 일시정지 (pause) | 피드백 동안 자동 pause, 종료 시 resume | **운동 템포 끊김** ⚠️ | ◎ | ◎ | 영상 컨트롤 추가 |
| 9-3. 기준 영상 무음 재생 | 영상 player `muted=true`, 피드백 TTS 만 | 끊김 없음 | ◎ | ✗ (BGM 손실, 선택권 박탈) | muted prop |
| 9-4. 피드백을 TTS 대신 자막+진동만 | 분기 5-B 로 통일 | 끊김 없음 | ○ (운동 중 화면 보기 어려움) | ◎ | 분기 5-B 활용 |
| 9-5. 사용자 토글 ("기준 영상 모드 시 TTS 약화") | 1~4 중 선택 | — | — | — | UX 복잡 |

### 9.2 추천 9-1 사유

- **운동 흐름 끊김 없음** — 9-2 의 영상 pause 는 동작 템포 깸. 9-3 은 사용자 선택권 박탈
- `expo-av` 의 `setAudioModeAsync` **한 줄 설정** — 발화 동안 OS 가 자동으로 다른 audio 의 볼륨을 30% 로 낮추고 발화 종료 시 100% 복원
- 기준 영상의 BGM·카운트가 완전히 끊기지 않고 작게 들리며 피드백 우선됨

### 9.3 감수 트레이드오프

- 기준 영상의 *핵심 코치 멘트* 가 피드백 발화 순간과 겹치면 그 부분이 작게 들림. 단 발화가 1~2초 수준이라 *치명적이지 않음*
- iOS·Android 의 ducking 동작이 약간 다름 — 단말 테스트 필요
- **`react-native-youtube-iframe` 호환성 위험** — 이 라이브러리는 내부 WebView 기반이라 native `expo-av` audio session 의 ducking 이 안 먹힐 수 있음. 그 경우 fallback:

| Fallback 옵션 | 비고 |
|------|------|
| (a) **9-2 영상 pause** | youtube-iframe 의 `play` prop 토글로 강제 일시정지. 단 운동 흐름 끊김 |
| (b) **iframe 자체 볼륨 조절** | youtube-iframe `getIframeProperties` 로 volume control. 일부 환경 미지원 |

진짜 영상 통합 시점에 1회 테스트하여 fallback 결정.

### 9.4 구현 골격

```typescript
// 앱 시작 시 1회
import { Audio } from 'expo-av';

await Audio.setAudioModeAsync({
  playsInSilentModeIOS: true,
  staysActiveInBackground: false,
  interruptionModeIOS: Audio.INTERRUPTION_MODE_IOS_DUCK_OTHERS,
  interruptionModeAndroid: Audio.INTERRUPTION_MODE_ANDROID_DUCK_OTHERS,
  shouldDuckAndroid: true,
});

// 발화는 분기 8-A 의 expo-speech 호출 그대로 — OS 가 자동 ducking
Speech.speak(message, { language: 'ko-KR', rate: userTtsSpeed });
```

### 9.5 책임 분담

| 주체 | 책임 |
|------|------|
| **Client (RN)** | 앱 시작 시 audio session ducking 설정, `expo-speech.speak()` 호출, youtube-iframe player 통합 |
| **OS (iOS/Android)** | 발화 시점 다른 audio source 볼륨 자동 ↓ + 종료 시 복원 |
| **Spring** | **없음** — ducking 은 클라+OS 단독 처리, 서버는 관여 안 함 |
| **AI 서버** | **없음** — feedback_type 송신까지만 (분기 7-1) |

---

## 10. 데이터 플로우 (8-A 채택 시 전체 흐름)

분기 1~9 결정을 모두 반영한 end-to-end 플로우. *운동 중 외부 호출 0* 임을 시각적으로 확인.

### 10.1 전체 다이어그램

```
┌──────────────────────────────────────────────────────────────┐
│                    [ 사용자 단말 — RN ]                       │
│   카메라 → 매 프레임 캡처                                     │
│        │  POST /pose  (base64 이미지)                        │
└────────│─────────────────────────────────────────────────────┘
         ▼ HTTP (매 프레임)
┌──────────────────────────────────────────────────────────────┐
│                  [ AI 서버 — FastAPI ]                       │
│  MediaPipe → landmark·각도 추출                              │
│        ▼                                                     │
│  세션 상태 갱신 (session_state.py)                           │
│        ▼                                                     │
│  rep 완료? ─── No ──▶ PoseResponse (landmark만)              │
│        │ Yes                                                 │
│        ▼                                                     │
│  squat_analyzer:                                             │
│    1. 4 결함 룰 평가 (분기 1: 1-B)                           │
│       ├ KNEE_OUT?  ├ KNEE_IN?                                │
│       ├ HIP_HIGH?  └ BACK_BENT?                              │
│    2. priority 최솟값 1개 선택 (분기 3: 3-A)                 │
│        ▼                                                     │
│  세션 메모리에 누적 (판정 이벤트 로그)                       │
│        ▼                                                     │
│  PoseResponse 응답:                                          │
│    rep_completed: true                                       │
│    sync_rate: 71.2                                           │
│    feedback_type: "KNEE_OUT"  ◀── 신규 (분기 7: 7-1)         │
└────────│─────────────────────────────────────────────────────┘
         ▼ HTTP 응답
┌──────────────────────────────────────────────────────────────┐
│                    [ 사용자 단말 — RN ]                       │
│  rep_completed && feedback_type 체크                         │
│        ▼                                                     │
│  templateCache["KNEE_OUT"] (분기 4: 4-A)                     │
│    = "무릎이 발끝을 넘었습니다"                              │
│        ▼                                                     │
│  rep % 5 == 0 ? → message += " 5회" (분기 3.A.RC: RC-2)      │
│        ▼                                                     │
│  ttsEnabled? (분기 5)                                        │
│   ┌────┴────┐                                                │
│   ▼ true    ▼ false                                          │
│  expo-speech .speak()    Toast + Haptics                     │
│   │                       (분기 5: 5-B)                      │
│   ▼                                                          │
│  iOS: AVSpeechSynthesizer / Android: TextToSpeech            │
│   ▼                                                          │
│  OS PCM 합성 → 스피커                                        │
│  (분기 9: audio ducking — YouTube BGM 자동 ↓)                │
│        ▼                                                     │
│        🔊                                                    │
└──────────────────────────────────────────────────────────────┘

  ・・・ 운동 계속 (모든 rep 위 흐름 반복) ・・・

         ▼ 세션 종료 (사용자가 "운동 종료" 버튼)
┌──────────────────────────────────────────────────────────────┐
│                  [ AI 서버 — FastAPI ]                       │
│  세션 누적 판정 이벤트 → batch (분기 2: 2-A)                 │
│        ▼                                                     │
│  POST /internal/feedback/batch                               │
│  Header: X-Internal-Token: ***                               │
│  Body: {                                                     │
│    session_id: 123,                                          │
│    events: [                                                 │
│      { feedback_type: "KNEE_OUT",                            │
│        sync_rate_at_trigger: 52.3,                           │
│        occurred_at: "2026-05-25T10:23:45+09:00" },           │
│      ...                                                     │
│    ]                                                         │
│  }                                                           │
└────────│─────────────────────────────────────────────────────┘
         ▼ HTTP (세션당 1회)
┌──────────────────────────────────────────────────────────────┐
│                   [ Spring 서버 ]                            │
│  /internal/feedback/batch 핸들러                             │
│    1. 내부 토큰 검증                                         │
│    2. session_feedback_logs 일괄 insert (트랜잭션)           │
│    3. 멱등성 처리 (sessionId 중복 방어)                      │
│        ▼                                                     │
│  (선택) BE-03 트리거 → LLM 종합 리포트 생성                  │
│        ▼                                                     │
│  사용자에게 리포트 화면 제공                                 │
└──────────────────────────────────────────────────────────────┘
```

### 10.2 시간축 (한 rep)

| t (ms) | 어디서 | 무엇이 |
|---|---|---|
| 0 | 단말 | 카메라 프레임 캡처 |
| 5 | 단말 | base64 인코딩 |
| 10 | 단말→AI | HTTP POST `/pose` |
| 30 | AI | MediaPipe landmark 추출 |
| 35 | AI | rep 완료 감지 |
| 36 | AI | 4 룰 평가 → `KNEE_OUT` |
| 37 | AI | priority 선택, 세션 누적 |
| 40 | AI→단말 | PoseResponse (feedback_type 포함) |
| 60 | 단말 | 응답 수신 |
| 61 | 단말 | templateCache lookup |
| 62 | 단말 | `Speech.speak()` 호출 |
| 65 | OS | 합성 시작 |
| 110 | OS | 스피커 첫 음 출력 🔊 |

→ **rep 종료 → 발화까지 약 75ms.** 사용자 즉시 인지.

### 10.3 외부 의존성 - 확인

```
운동 중 (rep 단위):
  ┌─ AI 서버 (우리)
  ├─ Spring (우리)
  ├─ OS TTS (단말 내장)
  └─ 외부 API: 0      ◀── 가치 1·2 hard requirement 통과

세션 종료 후:
  ├─ Spring → AI batch 수신
  └─ (선택) LLM (BE-03 — 별 분기, TTS 분기와 분리)
```

### 10.4 분기별 결정 반영 위치

| 분기 | 결정 | 플로우 어디서 |
|---|---|---|
| 1 | 1-B (AI 가 8종 분류) | `squat_analyzer` 의 4 룰 평가 |
| 2 | 2-A (판정 = 송신) | AI 세션 누적 → 종료 시 batch POST |
| 3 | 3-A (priority 1개) | `squat_analyzer` 후보 수집·선택 |
| 3.A.RC | RC-2 (5배수 카운트) | 단말 발화 핸들러 message append |
| 4 | 4-A (운동 진입 시 캐시) | 클라 운동 시작 화면에서 GET 호출 |
| 5 | 5-B (자막+진동) | `ttsEnabled=false` 분기 |
| 6 | 6-A (GOOD_FORM 무발화) | `squat_analyzer` 정상 시 `feedback_type=null` |
| 7 | 7-1 (HTTP 응답 확장) | `PoseResponse.feedback_type` 신규 필드 |
| 8 | 8-A (`expo-speech`) | OS TTS 호출 |
| 9 | 9-1 (audio ducking) | 앱 시작 시 `setAudioModeAsync` |

---

## 11. Spring 서버 책임 (TTS 피처 범위)

8-A 플로우에서 Spring 이 *해야 하는 일* 과 *안 해야 하는 일* 명시. 분기 9 의 §9.5 책임 분담 표를 TTS 피처 전체로 확장.

### 11.1 Spring 이 책임지는 영역

#### A. 운동 시작 전 (Setup)

| API | 책임 | 비고 |
|---|---|---|
| `GET /exercises/{id}/feedback-templates` | **토큰의 사용자 정보로 페르소나 자동 적용** → 해당 페르소나 템플릿 4개 반환 (스쿼트 한정) | 분기 4 의 4-A 캐시 로딩. 클라가 persona 파라미터 전달 불필요 |
| `GET /preferences/tts` | `ttsEnabled`, `ttsSpeed` 조회 | 요구사항 §8 |
| `PATCH /preferences/tts` | TTS 설정 갱신 | 요구사항 §8 |

**페르소나 정보 경로**:
- 사용자 페르소나는 기존 `users` 도메인의 `persona` 컬럼에 저장 (또는 user profile 응답에 포함)
- `GET /users/me` 응답에 `persona` 필드 포함 — 클라는 로그인 시 캐시
- `PATCH /users/me/persona` — 사용자가 페르소나 변경 (12-persona-difficulty.md)
- *Spring 의 `GET /exercises/{id}/feedback-templates`* 는 토큰에서 사용자 ID → persona 조회 → 필터링. **클라는 persona 모르고도 호출 가능** — 책임 단일화

#### B. 세션 종료 시 (Batch)

| API | 책임 | 비고 |
|---|---|---|
| `POST /internal/feedback/batch` | AI 송신 판정 이벤트 수신 → DB 적재 | 분기 2 의 2-A |

세부 처리:
- 인증: `X-Internal-Token` 헤더 검증 (외부 노출 금지)
- 트랜잭션: `session_feedback_logs` 일괄 insert
- 멱등성: 같은 sessionId 의 중복 송신 방어 (uniq index 또는 upsert)
- 검증: `feedbackType` 이 8종 enum 인지 화이트리스트 확인

#### C. 세션 후 조회

| API | 책임 | 비고 |
|---|---|---|
| `GET /sessions/{id}/feedbacks` | 세션의 판정 이벤트 리스트 | 본인·트레이너 권한 |
| `GET /sessions/{id}/feedback-summary` | 8 결함별 카운트, 평균 sync_rate 집계 | 리포트 화면용 |

#### D. 데이터 모델·운영

- `exercise_feedback_templates` 테이블 운영 (관리자 CRUD)
- `session_feedback_logs` 테이블 인덱스 관리 (`session_id`, `occurred_at`)
- `feedback_type` enum 코드 관리 (`FeedbackType.java`)
- `persona` enum 관리 (`Persona.java`)

#### E. 추후 (BE-03 영역, TTS 분기와는 분리)

- LLM 호출 → 종합 코칭 글 생성
- `session_reports` 테이블 저장
- 트리거: batch insert 성공 후 비동기 워커

### 11.2 Spring 이 **책임지지 않는** 영역 (명시)

| 영역 | 책임 주체 | 이유 |
|---|---|---|
| 8종 enum 분류 (자세 → feedback_type) | **AI 서버** | landmark 가 AI 쪽에만 흐름. 분류 로직 책임 단일화 (분기 1: 1-B) |
| 다중 결함 시 1개 선택 | **AI 서버** | priority 평가가 분류와 결합 (분기 3: 3-A) |
| 멘트 생성·변환 | **클라 (DB 캐시 lookup)** | 정적 매핑이므로 DB 조회만으로 충분 (분기 4) |
| TTS 합성 | **단말 OS** | `expo-speech` → AVSpeechSynthesizer / TextToSpeech (분기 8: 8-A) |
| audio ducking | **단말 OS** | `setAudioModeAsync` 1회 설정으로 OS 가 자동 (분기 9: 9-1) |
| 자막·진동 fallback | **클라** | UX 로직이라 서버 무관 (분기 5: 5-B) |
| 운동 중 발화 트리거 결정 | **AI 서버** → 클라 직결 | Spring 경유 시 latency 추가 (분기 7: 7-1 — 7-4 거부 이유) |
| LLM 호출 (운동 중) | **누구도 안 함** | 가치 1·2 위배 — *운동 중 외부 호출 금지* 원칙 |

### 11.3 신규 작업 (BE 측)

| 항목 | 위치 | 분량 |
|---|---|---|
> **⚠️ 2026-05-25 갱신 11 — 코드 실태 확인 결과**: §11.3 항목 대부분이 이미 구현되어 있음 (커밋 `2f48526`, 2026-05-09). 아래 표는 **실태를 반영한 정정판**. 진짜 신규 작업은 5건으로 **~120~150줄** 수준 (당초 추정 230~290줄에서 정정).

| 항목 | 실제 상태 | 위치 | 신규 분량 |
|------|---------|------|---------|
| **— Schema (mysql/schema.sql, data.sql) —** | | | |
| `exercise_feedback_templates` 테이블 | ✅ 존재 — uniqueKey `(exercise_id, feedback_type)`. 단 `persona` 컬럼 **없음** | `mysql/schema.sql:120`, `data.sql:79` | — |
| `session_feedback_logs` 테이블 | ✅ 존재 — 인덱스 `(session_id, occurred_at)` | `mysql/schema.sql:132` | — |
| `members.tts_enabled`, `tts_speed` 컬럼 | ✅ 존재 (default `TRUE`, `1.0`) | `mysql/schema.sql:23-24` | — |
| `members.selected_persona` 컬럼 | ✅ 존재 (enum BEGINNER/ADVANCED/DIET/REHAB) | `mysql/schema.sql:20` | — |
| `exercise_feedback_templates` seed | ✅ 존재 — 스쿼트 4건 (`KNEE_OUT`, `KNEE_IN`, `HIP_HIGH`, `BACK_BENT`), 런지 3건, 플랭크 4건. **모두 페르소나 무관 단일 멘트** | `mysql/data.sql:130-147` | — |
| **A. `persona` 컬럼 추가 + uniqueKey 변경** ⭐ | ❌ 미구현 — `ExerciseFeedbackTemplate` 엔티티 + DDL 변경 + uniqueKey `(exercise_id, feedback_type, persona)` | `mysql/schema.sql:120-128`, `model/exercise/ExerciseFeedbackTemplate.java` | ~10줄 |
| **B. 페르소나 × 결함 16 row seed** ⭐ | ❌ 미구현 — 스쿼트 4 결함 × 4 페르소나 = 16 row (또는 단계적: BEGINNER 4 row 만 먼저) | `mysql/data.sql` | ~20줄 |
| **— Endpoints / Service —** | | | |
| `InternalFeedbackController.batch()` (`POST /internal/feedback/batch`) | ✅ 존재 — `X-Internal-Token` 검증 + `FeedbackLogService.saveBatch()` 호출 | `controller/InternalFeedbackController.java` | — |
| `FeedbackLogService.saveBatch()` (= 당초 명명 `SessionFeedbackService.batchInsert`) | ✅ 존재 | `service/Exercise/FeedbackLogService.java` | — |
| `FeedbackBatchRequestDto` | ✅ 존재 | `dto/exercises/feedback/FeedbackBatchRequestDto.java` | — |
| 내부 토큰 검증 | ✅ inline 처리 (Controller 안) | 위 InternalFeedbackController | — |
| `FeedbackTemplateController.list()` (`GET /exercises/{id}/feedback-templates`) | ⚠️ 존재하나 **페르소나 필터링 없음** — 운동별 전체 반환 | `controller/FeedbackTemplateController.java` | — |
| **C. 페르소나 자동 필터 (Controller 갱신)** ⭐ | ❌ 미구현 — 토큰의 `selectedPersona` → repo `findByExerciseIdAndPersona` 필터 | `controller/FeedbackTemplateController.java`, `service/Exercise/FeedbackTemplateService.java`, repo | ~30줄 |
| `PreferenceController.get/patch()` (`GET/PATCH /preferences/tts`) | ✅ 존재 — `members.tts_*` 직접 갱신 | `controller/PreferenceController.java`, `service/Member/PreferenceService.java` | — |
| **D. `SessionController.end()`** (분기 2.A.ET 의 ET-A — `PATCH /sessions/{id}/end`) ⭐ | ❌ 미구현 — Session 엔티티에 `endTime` 컬럼은 있으나 endpoint 없음 | `controller/SessionController.java`, `service/Exercise/SessionService.java` | ~25줄 |
| **E. `SessionFeedbackController.list/summary()`** (`GET /sessions/{id}/feedbacks`, `feedback-summary`) ⭐ | ❌ 미구현 | `controller/SessionFeedbackController.java`, `service/Exercise/SessionFeedbackQueryService.java` | ~60줄 |
| 권한 가드 (본인·트레이너) | 기존 권한 모듈 재사용 | — | — |
| `22-backend-tasks-detail.md` 에 BE 작업 항목 신설 | ❌ 미구현 (이 정정 진행 중) | 문서 | 1 작업 |

→ **Spring 측 진짜 신규 ~120~150줄.** 5건 (A·B·C·D·E) 작업.
→ A·B 는 BE-30 (TTS 효과 분석) 의 페르소나 확장 영역. C·D·E 는 신규 BE 작업으로 22-backend-tasks-detail.md 에 추가.

**선행 작업 (TTS 분기 범위 밖, 이미 구현됨)**:
- `POST /sessions` (세션 시작 — session_id 발급) — ✅ 기존
- `members.selected_persona` 컬럼 — ✅ 기존
- 따라서 *선행 차단 요소 없음* — 작업 즉시 시작 가능

### 11.4 운동 중 Spring 부담 명시

| 트래픽 | 빈도 | 부담 |
|---|---|---|
| 매 프레임 `/pose` (AI 직결) | ~10~30 fps × 사용자 수 | **AI 가 받음 — Spring 무관** |
| rep 완료 응답 | rep 마다 (AI ↔ 클라) | **Spring 무관** |
| 세션 종료 batch | 세션당 1회 | Spring 한 번. 부담 미미 |
| 운동 시작 시 `GET /exercises/{id}/feedback-templates` | 운동 진입 1회 | 캐시·인덱스로 미미 |

→ Spring 의 *운동 중 실시간 부담은 0*. 요구사항 §1 "실시간 부하 분리" 정합.

---

## 12. 구현 전 협의 안건

§10·§11 의 데이터 플로우·책임이 정해졌더라도, *경계 계약* (API schema, 동작 정책) 은 Front (React Native) 와 FastAPI (AI server) 담당자와 사전 합의해야 함. 합의 누락 시 통합 단계에서 재작업 발생.

### 12.1 Front (React Native) 와 협의

#### A. `GET /exercises/{id}/feedback-templates`

| 안건 | 결정 필요 사항 |
|---|---|
| 응답 구조 | `{ "KNEE_OUT": "무릎이…" }` Map 형태 vs `[{type, message, priority}]` Array |
| 호출 시점 | 운동 진입 시 매번 vs 앱 시작 시 1회 (분기 4-A 추천: 운동 진입 시 1회) |
| 캐시 무효화 | 사용자가 페르소나 변경 직후 templateCache 무효화 방법 |
| 빈 결과 처리 | 페르소나 row 없으면 BEGINNER fallback vs 빈 배열 vs 404 |
| 응답 메타 포함 | `priority` 응답 포함 여부 (클라는 priority 안 씀 → 제외 가능) |

#### B. `GET/PATCH /preferences/tts`

| 안건 | 결정 필요 사항 |
|---|---|
| 요청 body | `{ ttsEnabled, ttsSpeed }` — 둘 다 필수 vs PATCH 부분 업데이트 허용 |
| `ttsSpeed` 검증 | 0.5~2.0 범위 위배 시 422 vs 자동 클램프 |
| 즉시 효과 발현 | 현재 운동 중 변경 시 다음 rep 부터 vs 다음 세션부터 |
| default 값 | 신규 사용자 자동 row 생성 (`ttsEnabled=true`, `ttsSpeed=1.0`, 요구사항 §8) |

#### C. 분기 2.A.ET — 세션 종료 trigger (ET-A)

| 안건 | 결정 필요 사항 |
|---|---|
| `PATCH /sessions/{id}/end` body | empty vs `{ endedAt }` 클라 전달 (서버 시각 권위 vs 클라 시각) |
| 양방향 호출 순서 | Spring·AI 동시 (`Promise.all`) vs 직렬 (Spring → AI) |
| 부분 실패 처리 | Spring 실패 + AI 성공 시? AI 실패 + Spring 성공 시? |
| AI 응답 대기 | 클라가 AI batch 완료까지 대기 vs fire-and-forget |
| 재시도 정책 | 한쪽 실패 시 재시도 횟수·backoff |

#### D. 조회 API (`GET /sessions/{id}/feedbacks`, `feedback-summary`)

| 안건 | 결정 필요 사항 |
|---|---|
| 응답 schema | events list, summary 의 필드명·타입 |
| 페이징 | 결함 수 많을 때 page·size (rep 30 × 결함 7 = ~210건 가능) |
| 권한 | 본인 외 트레이너 권한 토큰·헤더 |
| summary 집계 단위 | feedback_type 별 카운트만 vs sync_rate avg/min/max 포함 |
| 시간대 | `occurredAt` UTC vs Asia/Seoul, ISO 8601 형식 |

#### E. enum 표기

| 안건 | 결정 필요 사항 |
|---|---|
| 대소문자 | `KNEE_OUT` (UPPER_SNAKE) 통일 — Spring enum 명·Front 문자열 일치 |
| enum 추가 시 배포 순서 | Spring 먼저 (DB seed + enum) → Front (캐시 키 인식) |

### 12.2 FastAPI (AI server) 와 협의

#### A. `POST /internal/feedback/batch` (핵심)

| 안건 | 결정 필요 사항 |
|---|---|
| payload schema | **snake_case 채택** (Pydantic 기본·proto 공식 컨벤션). `{session_id, events:[{feedback_type, sync_rate_at_trigger, occurred_at}]}`. Spring 측 DTO 에 `@JsonNaming(SnakeCaseStrategy.class)` 2줄 추가 — AI 코드 0 변경 ([[feedback-minimize-python-changes]] 정합) |
| 내부 토큰 운영 | `X-Internal-Token` 값 관리 위치 (환경변수·secret manager), 회전 정책 |
| 타임아웃 | Spring 응답 대기 (5초·10초?) |
| 재시도 정책 | Spring 5xx 시 AI 재시도 — 횟수·backoff·종료 조건 |
| 부분 실패 | events 일부 invalid 시 — 전체 reject vs 유효한 것만 insert + reject 목록 응답 |
| 멱등성 | 같은 sessionId 재송신 — upsert vs unique constraint 위반 시 200 vs idempotency-key 헤더 |
| 응답 형식 | `200 OK { insertedCount }` vs `204 No Content` |
| batch 크기 한도 | events 최대 개수 (200·500?) — 초과 시 split |

#### B. 분기 2.A.ET — AI 측 종료 신호 수신

| 안건 | 결정 필요 사항 |
|---|---|
| 신호 형식 | 마지막 `POST /pose` 의 `session_end=true` 플래그 vs 별도 `POST /sessions/{id}/end` endpoint |
| batch 송신 시점 | 종료 신호 수신 즉시 동기 송신 vs 백그라운드 task |
| 세션 메모리 정리 | batch 송신 성공 후 AI 누적 데이터 삭제 시점 |
| 종료 신호 누락 처리 | 클라가 신호 안 보내고 앱 종료 시 — timeout safety net 도입 여부 (ET-C 거부했으나 safety net 으로 둘 가능성) |

#### C. proto 동기화

| 안건 | 결정 필요 사항 |
|---|---|
| `RepCompletedEvent.feedback_type` | proto 파일 양쪽 (ai-server + backend) 필드 번호·타입 (string vs enum) |
| `PoseResponse.feedback_type` (분기 7-1) | 신규 필드 null/empty 표현 방식 |
| proto 변경 배포 순서 | AI·Spring 동시 배포 강제 vs optional 필드로 호환성 |

#### D. 임계값·priority

| 안건 | 결정 필요 사항 |
|---|---|
| 임계값 위치 | `squat_analyzer` 코드 상수 vs config 파일 vs DB — AI 단독 결정이나 Spring·Front 가 알면 디버깅·튜닝에 도움 |
| 튜닝 책임 | AI 작업자 단독 vs 3자 의견 |
| priority 상수 위치 | AI 측 상수 (분기 3-A-1, 추천) vs Spring 에서 부팅 시 fetch (3-A-2) |

### 12.3 3자 모두 협의 필요

| 안건 | 결정 필요 사항 |
|---|---|
| **8종 enum 정확한 표기** | proto, Spring `FeedbackType.java`, Python 상수, Front 문자열, DB seed — **5곳** 일치. master 가 어디인지 명시 (proto vs REQUIREMENTS.md §6) |
| **페르소나 enum** | `BEGINNER/ADVANCED/DIET/REHAB` — Spring enum, Front 라벨 ("헬린이"/"헬창" 등), AI 측 (가능성). drift 방지 |
| **시간대·시간 형식** | `occurredAt`, `endedAt`, `createdAt` ISO 8601 + timezone (`+09:00` 또는 `Z`). 클라 표시 KST, DB UTC vs KST 어느 쪽 |
| **인증·내부 토큰 분리** | 사용자 토큰 (JWT, Front ↔ Spring 기존) + 내부 토큰 (`X-Internal-Token`, AI ↔ Spring 신규) — endpoint 분리 (`/api/` vs `/internal/`) |

### 12.4 우선순위 (1학기 MVP)

| 우선 | 안건 |
|---|---|
| 🔴 최우선 | 8종 enum 표기 통일, `POST /internal/feedback/batch` payload schema, proto 동기화 |
| 🟡 중요 | ET-A 의 클라 양방향 호출 순서·실패 처리, 페르소나 enum, 임계값 튜닝 |
| 🟢 차순위 | summary 집계 단위, 페이징, 시간대 표기, 내부 토큰 회전 정책 |

### 12.5 합의 산출물

- 8종 enum + 페르소나 enum 의 정식 정의 — `docs/REQUIREMENTS.md` §6 master 화 또는 별도 enum 명세
- batch API 명세 — `docs/07-api-design.md` 에 `POST /internal/feedback/batch` + 종료 trigger 추가
- proto 명세 — `ai-server/app/proto/exercise.proto` + `backend/src/main/proto/exercise.proto` 동기화 PR
- AI 측 협의 안건 별도 추출 — `docs/handoff/ai-tts-feedback-batch.md` (별도 문서)

### 12.6 통합 체크리스트 (작업용)

28건의 협의 안건을 *작업 체크리스트* 로 통합 정리한 별도 문서:
- **[`../handoff/tts-negotiation-checklist.md`](../handoff/tts-negotiation-checklist.md)** — 우선순위 🔴🟡🟢 표 + Spring API 매핑 + 당사자별 추출 + Spring API 별 영향도 + 진행 권장 순서

§12.1~12.4 의 분산 표는 *분야별 검토용*, 체크리스트는 *진행 추적용*. 합의 결정 시 체크리스트의 *상태* 컬럼에 체크 + 결정 내용 기록.

**Spring API 별 영향도 핵심**:
- `POST /internal/feedback/batch` — 관여 안건 10건 (최대 부담)
- `GET /exercises/{id}/feedback-templates` — 6건
- `GET /sessions/{id}/feedbacks` — 5건

→ Spring 담당자가 batch endpoint schema·정책을 먼저 확정 후 AI 담당자에게 전달이 자연스러운 흐름.

---

## 의식적으로 채택 안 한 옵션

| 안 한 것 | 이유 |
|---------|------|
| 서버 TTS 오디오 합성 | RTT·비용·오프라인 불가. device TTS 로 충분 |
| 시간 기반 쓰로틀(3초/5초 룰) | rep 단위 발화로 자연 쓰로틀 — 정책 자체 무의미 |
| HIGH/MEDIUM/LOW 발화 큐 | rep 1회 = 발화 0~1회라 큐 불필요 |
| GOOD_FORM 발화 | 요구사항 §6 8종 enum 에 없음 — 6-A |
| 클라 → AI → Spring 우회 송신 | AI 가 자기 판정을 자기가 직접 송신 (2-A) 가 단순 |
| 운동별 priority 상수 분기 | [[project-squat-first]] — 스쿼트만 priority 매핑 |
| LLM 동적 멘트 | 비용·지연. BE-03 이후 |
| `ELBOW_BENT`·`HEAD_DOWN` 등 스쿼트와 무관한 4종 즉시 분류 | 스쿼트 단일 운동 — DB 템플릿에 등록 안 함, AI 분류 함수도 비활성 |

---

## AI 변경 비용 정리 (분기 1 + 2 + 3 합산)

요구사항이 명시한 부분이라 사용자 결정 사항은 *구현 시기·범위* 뿐.

| 항목 | 변경 위치 | 분량 |
|------|---------|------|
| proto `RepCompletedEvent.feedback_type` 필드 | `ai-server/app/proto/exercise.proto` + `backend/src/main/proto/exercise.proto` | 1줄 ×2 |
| `PoseResponse.feedback_type` 필드 (분기 7-1) | `ai-server/app/models/pose.py` + `pose.py` rep 분기 return | 2줄 |
| 스쿼트 4종 분류 함수 (`KNEE_OUT`/`KNEE_IN`/`HIP_HIGH`/`BACK_BENT`) | `squat_analyzer.py` | 함수 1개 (약 30~50줄) |
| priority 상수 + 다중 검출 시 1개 선택 | `squat_analyzer._summarize_rep` 또는 별도 헬퍼 | 약 10줄 |
| 판정 이벤트 누적 (세션 메모리) | `session_state.py` | 약 5줄 |
| 세션 종료 시 batch POST (`/internal/feedback/batch`) | `spring_client.py` | 메서드 1개 (약 15줄) |
| 23-ai-tasks-detail.md 에 작업 항목 신설 | 문서 | 1 작업 |

→ 다음 PR 단위로 분리: (a) proto + 분류 함수 + priority, (b) 누적 + batch POST.

---

## 측정·검증 (구현 후)

| 항목 | 측정 방법 |
|------|---------|
| 8종 분류 정확도 | 사용자 영상 5건 → 사람 판정 vs AI 분류 일치도. 스쿼트 활성 4종 한정 |
| rep 단위 1발화 보장 | 한 rep 안에서 클라가 받은 `feedback_type` 개수 ≤ 1 |
| batch 누락 | 세션 종료 시 `session_feedback_logs` 행 수 == AI 측 누적 카운터 |
| TTS off + 자막 동작 | `ttsEnabled=false` 로 세션 진행 시 토스트·진동 검증, 로그는 정상 적재 |

---

## 향후 재검토 트리거

| 사건 | 다시 봐야 할 결정 |
|------|---------------|
| 런지·플랭크 도입 | 분기 1 (8종 중 `ELBOW_BENT`·`HEAD_DOWN`·`SHOULDER_TILT`·`HIP_HIGH` 활성화), 분기 3 (운동별 priority 필요할 수 있음) |
| BE-03 (LLM 리포트) | 의식 제외의 "LLM 동적 멘트" 재검토 |
| 사용자 "rep 끝나고 발화 너무 늦다" | 분기 2 (실시간 송신 vs rep 종료 송신) — 단 현재 rep 종료가 자연 트리거 |
| 사용자 "한 rep 에 결함 2개인데 1개만 알려줘서 불편" | 분기 3 (3-A → 3-C 다중 발화 전환) |
| 오프라인 사용 요구 | 분기 4 (영속 캐시 도입) |
| 매 rep 칭찬 받고 싶다는 사용자 피드백 | 분기 6 (6-A → 6-B 변경, 요구사항 갱신 필요) |
| 매 프레임 HTTP POST latency·배터리 병목 측정됨 | 분기 7 (7-1 → 7-2 gRPC bidi 또는 7-3 WS 격상) |
| Spring 이 발화 결과를 실시간으로 알아야 할 신규 요구 | 분기 7 (7-4 도입 가능성) — 현재 요구사항엔 없음 |
| 사용자 피드백 "안 들림" 5%↑ 또는 Android 단말 음질 이슈 | 분기 8 (8-A → 8-D 격상, 8.4 트리거) |
| 정식 출시 시 브랜드 음성 요구 | 분기 8 (8-A → 8-B 또는 8-D) |

---

## 결정 로그

- **2026-05-25 (초안)**: 5개 분기 추천안 작성. 요구사항 미확인으로 분기 2 가 잘못 작성됨 (Client → Spring 직송 추천).
- **2026-05-25 (갱신 1)**: 사용자 지적으로 REQUIREMENTS.md §5·6·8 반영. 변경 사항:
  - 분기 1: 1-B 유지, 비용 재산정 (8종 분류 로직 신설 명시)
  - 분기 2: 2-A → **2-A 새 정의 (판정 이벤트 = 송신 이벤트)** 로 정정. 요구사항이 송신 주체를 AI 로 명시
  - 분기 3: 시간 쓰로틀·큐 정책 전면 폐기, 다중 검출 tie-break (3-A) 으로 재정의
  - 분기 6 신설: GOOD_FORM 발화 안 함 (6-A)
  - AI 변경 비용 정리 섹션 신설
- **2026-05-25 (갱신 2)**: 데이터 플로우 분기 7 신설. `pose.py` 코드 확인으로 Client ↔ AI HTTP 직결 채널이 이미 동작 중임을 전제로 함:
  - 7-1 (HTTP response 확장) 추천 — PoseResponse 에 `feedback_type` 1 필드 추가
  - 7-2 (gRPC bidi)·7-3 (WS/SSE) 는 latency 병목 측정 전엔 premature
  - 7-4 (Spring 경유) 는 요구사항 §1 "실시간 부하 분리" 와 충돌
  - 7-5 (Client 자체 분류) 는 분기 1 의 1-B 와 충돌
- **2026-05-25 (갱신 4)**: 사용자 결정 B/E/F/G 반영 + 분기 9 신설:
  - **B**: GOOD_FORM 발화 안 함 — 분기 6 ACCEPTED
  - **E**: 사용자 답변이 질문 의도와 어긋남 (TTS 가 한국어인 건 확정, off 시 처리는 미확정). 분기 5 OPEN 유지 — 다음 검토 시 명시적 confirm 필요
  - **F**: rep 카운트 발화 포함 — 분기 3 안에 §3.A.RC 신설, **RC-2 (5의 배수 마일스톤)** 추천. 결함 발화와 동시 발생 시 결함 우선·카운트는 이어 붙임
  - **G**: 분기 9 신설 — 기준 영상 ducking. 추천 9-1 (audio ducking, `expo-av` 1줄 설정). youtube-iframe 호환성은 통합 시 1회 테스트로 확인. **Spring 책임 없음** (클라+OS 단독)
- **2026-05-25 (갱신 5)**: 분기 8 안에 "후보 4 갈래로 묶어 보기 (큰 그림)" 섹션 추가. 7개 후보를 ①사전캐시 / ②on-device / ③cloud실시간 / ④LLM / ⑤혼합 으로 묶어 설명. 기존 §8.1~§8.7 번호 변경 없음.
- **2026-05-25 (갱신 6)**: 갱신 5 의 ①~⑤ 갈래(*음성 합성 위치* 한 축) 위에 **2차원 분류** 섹션 추가. *텍스트 생성 위치* 축을 추가하여 7개 후보를 2×2 매트릭스에 배치. 왼쪽 아래(텍스트 외부+음성 내부)는 비용·합리성 측면에서 의미 없음. 8-G 단독으로 오른쪽 아래(텍스트·음성 모두 외부) 차지. 현재 추천(8-A → 8-D)이 왼쪽 열에 위치함을 명시 — *운동 중 발화에는 LLM 미사용* 일관 원칙 확인.
- **2026-05-25 (갱신 7)**: §10 "데이터 플로우 (8-A 채택 시 전체 흐름)" + §11 "Spring 서버 책임" 신설. 분기 1~9 결정을 end-to-end 다이어그램으로 통합. Spring 의 책임/비책임 영역을 명시 — Spring 은 (a) Setup API 3개, (b) batch 수신 1개, (c) 조회 API 2개, (d) 데이터 모델 운영, (e) BE-03 (추후) 만 담당. 8종 분류·멘트 생성·TTS 합성·ducking 등은 Spring 비책임으로 명시. 신규 BE 작업 ~150~180줄, BE-10 의 일부로 분류 가능. 운동 중 Spring 실시간 부담 0 (요구사항 §1 정합).
- **2026-05-25 (갱신 16)**: **협의 안건 #14·#15 (preferences) 결정 진행 + #15 보류 전환**:
  - #14 ttsSpeed 검증: ✅ UI 슬라이더로 0.5~2.0 범위 강제 + Spring `@DecimalMin("0.5") @DecimalMax("2.0")` 표준 검증 어노테이션 (방어용, 평시 발동 X)
  - #15 TTS preferences 즉시 효과: 🔵 *보류* — 일단 cached value 추천 박제했으나 *Front UI 디자인 확정 후 재검토 필요* (운동 중 변경 UI 만들 가능성). 사용자 결정으로 보류 전환
  - 사용자 명시적 confirm 항목 5건 → **6건** (#14 만 추가)
- **2026-05-25 (갱신 15)**: **협의 안건 #16 (시간대 형식) 단순화 결정**:
  - 한국 전용 서비스 ([[project-korean-only]]) 정합 — timezone 마커 가치 약하고 산업 mainstream (카카오·네이버·토스) 도 미사용
  - 결정: (1) 서버 timezone Asia/Seoul 고정 (Spring `spring.jackson.time-zone: Asia/Seoul` + AI `TZ=Asia/Seoul`), (2) API JSON 형식 *timezone 마커 없음* (`"2026-05-25T10:23:45"`), (3) DB `LocalDateTime` 유지, (4) UI KST 표시
  - 작업 영향: `application.yml` + `docker-compose.yml` 각 1줄. 코드 변경 0
  - 글로벌 진출 시 재검토 (현재 가능성 0)
  - 미팅 안건 4 *사전 해결* — 3자 미팅에서 제외 가능. tts-negotiation-checklist.md #16 ✅, 3way-meeting-agenda.md 안건 4 단순화안으로 갱신
- **2026-05-25 (갱신 14)**: **BT-SET 작업 분담 명시 + 점진 전환 단계 박제**:
  - §2.A.BT 에 *책임 분담* sub-섹션 신설 — Spring (DTO·uniqueKey·INSERT IGNORE) ~10줄 + AI (set 카운터·retry·target_reps_per_set 자체 계산) ~60줄. **Front 변경 없음** (분기 7-1 의 발화 채널만 관여)
  - §2.A.BT 에 *점진 전환* 4 단계 명시 — Phase 1 (MVP, BT-NONE) → Phase 2 (BE-13, Spring 준비) → Phase 3 (베타, AI 전환) → Phase 4 (정식, 디스크 영속화). **Spring 측 작업은 Phase 2 한 번으로 끝**
  - `22-backend-tasks-detail.md` BE-13 에 *작업 B (BT-SET 지원)* 5 항목 추가 (~10줄, +0.5h). 협의 안건 #3·#10 BE-13 리스크 표에 명시
  - `ai-tts-feedback-batch.md` 보강:
    - 작업 패키지 추정 정정 (~100~130줄, 5~7h)
    - *target_reps_per_set 수신 방법* 명시 — Persona + difficultyLevel 로 AI 자체 계산 권장 (Spring 무수정)
    - 통신 컨벤션 snake_case 명시
- **2026-05-25 (갱신 13)**: **분기 2.A.BT (송신 trigger 분할) 신설 + BT-SET (세트 경계 batch) 채택**:
  - 분기 2-A 가 *세션 종료 1회* 로만 결정되어 있어 *강제 종료/AI 크래시/네트워크 단절 시 전체 세션 손실* 위험 존재
  - 5 옵션 비교 (BT-NONE 현행 / BT-REP per-rep / BT-5REP 5rep batch / BT-SET 세트 경계 / BT-TIME 시간 timer):
    - BT-REP: Spring 자원 비효율 (commit 30회), retry 예산 부족 (~2.5s)
    - BT-SET ⭐: 운동 도메인 자연 단위, 휴식 시간 (30~90s) 활용 retry, 호출 효율, 운영 mainstream (Strava·Apple Fitness·Peloton)
  - **세트 인지 방법**: `12-persona-difficulty.md` 의 `targetReps` 기반 카운터. AI `session_state.py` 에 `current_set_reps`·`current_set_no`·`events_buffer` 추가 (~10~15줄)
  - **휴식 시간 활용 retry**: 0s/5s/15s/35s backoff, 총 ~55s. 다음 set 시작 전 거의 100% 성공
  - **payload 확장**: `set_no`, `is_final` 필드 추가 (협의 안건 #3 갱신)
  - **멱등성 필수** (협의 안건 #10): `session_feedback_logs` 에 `(session_id, occurred_at, feedback_type)` uniqueKey + `INSERT IGNORE`. retry 의 정상 운영을 위함
  - **손실 최대 1 set** (~10 rep): 운영 진입 시 디스크 영속화 (옵션 C, AI SQLite WAL) 결합으로 0 가능
  - **도입 시점**: 1학기 MVP 시연은 BT-NONE 도 OK / 베타 (50+명) 진입 전 BT-SET 도입 / 정식 (1000+명) 시 + 디스크 영속화
  - 4 문서 갱신: tts-design.md §2.A.BT 신설, ai-tts-feedback-batch.md §E set-boundary 흐름·retry·DTO 확장 명시, tts-negotiation-checklist.md #3·#10 갱신
- **2026-05-25 (갱신 12)**: **협의 안건 #3 (batch payload schema) snake_case 채택**:
  - 당초 §2 의 payload 예시·§10 다이어그램·§12 안건 모두 camelCase 가정으로 작성
  - 작업량 비교 결과: AI 측 Pydantic alias_generator (~15~20줄) vs Spring 측 `@JsonNaming(SnakeCaseStrategy.class)` 어노테이션 2줄. **Spring 측 2줄이 가장 가벼움** + [[feedback-minimize-python-changes]] 정합 (AI 코드 0 변경)
  - 4 문서 정정: tts-design.md §2 payload·§10.1 다이어그램·§12 안건, tts-negotiation-checklist.md #3 표·상세 설명, ai-tts-feedback-batch.md `spring_client.py` 예시·협의 코멘트, 3way-meeting-agenda.md 미팅 밖 협의 표에 #3 ✅ 추가
  - **결정**: snake_case 채택. Spring 측 `FeedbackBatchRequestDto`, `FeedbackEventDto` 2개에 `@JsonNaming` 어노테이션 추가는 BE-13 시점에 처리. Java 필드명 변경 불필요
- **2026-05-25 (갱신 11)**: **코드 실태 확인 후 §11.3 정정 + 분기 1 의 HIP_LOW/HIP_HIGH 표기 오류 수정**:
  - §11.3 의 "신규 작업 230~290줄" 추정이 *과대평가* 였음. 커밋 `2f48526` (2026-05-09) 으로 이미 다음이 구현됨:
    - `InternalFeedbackController` (`/internal/feedback/batch`), `FeedbackTemplateController` (페르소나 필터 제외), `PreferenceController` (`/preferences/tts`), `FeedbackLogService.saveBatch`, `FeedbackBatchRequestDto`, `FeedbackType` enum 8종, `ExerciseFeedbackTemplate` 엔티티 (단 persona 컬럼 없음), `SessionFeedbackLog`, `members.selected_persona/tts_enabled/tts_speed`, seed 데이터 (스쿼트 4건 등)
  - 진짜 신규는 5건 (~120~150줄): **A** persona 컬럼 + uniqueKey 변경, **B** 페르소나 × 결함 16 row seed, **C** 페르소나 자동 필터 (Controller 갱신), **D** `SessionController.end()` (ET-A), **E** `SessionFeedbackController.list/summary()`
  - **분기 1·§10·§11·AI handoff 의 스쿼트 4종 표기 정정**: `HIP_LOW` → **`HIP_HIGH`**. 스쿼트의 "충분히 못 내려감" 은 *엉덩이가 들려 있는* 상태이므로 `HIP_HIGH`(엉덩이 과도하게 들림)가 정합. seed `data.sql:130-134` 와도 일치. `HIP_LOW`(엉덩이 처짐) 는 플랭크 전용 (`data.sql:145`)
  - 신규 작업 5건은 `22-backend-tasks-detail.md` 에 BE-13 (페르소나 분기 적용) + BE-14 (Session 종료 endpoint) + BE-15 (세션 피드백 조회 API) 로 추가
- **2026-05-25 (갱신 10)**: §12.6 신설 + 통합 체크리스트 문서 분리:
  - **§12.6** — 28건 협의 안건을 우선순위 🔴🟡🟢 표 + Spring API 매핑으로 통합. §12.1~12.4 분산 표는 *분야별 검토*, 체크리스트는 *진행 추적* 으로 역할 분리
  - **`docs/handoff/tts-negotiation-checklist.md`** 신설 — 작업용 체크리스트. 28건 × (안건 / 관련 Spring API / 결정 옵션 / 당사자 / 상태) 컬럼. 당사자별 추출, Spring API 별 영향도 (batch endpoint 가 10건 최대 부담), 합의 산출물 7종, 진행 권장 순서 5단계 명시
- **2026-05-25 (갱신 9)**: §12 "구현 전 협의 안건" 신설 + AI 측 별도 handoff 문서 신설:
  - **§12**: §10·§11 의 데이터 플로우·책임이 정해져도 *경계 계약* (API schema·동작 정책) 은 사전 합의 필요. Front 협의 5종 (templates/preferences/세션종료/조회/enum), AI 협의 4종 (batch/종료신호/proto/임계값), 3자 협의 4종 (enum·페르소나·시간대·토큰 분리). 우선순위 🔴🟡🟢 분류, 합의 산출물 4종 명시
  - **`docs/handoff/ai-tts-feedback-batch.md`** 신설 — AI 담당자 대상 작업 요청서. proto 확장·`PoseResponse` 신규 필드·4종 분류 함수·세션 누적·batch POST·종료 신호 수신 6 항목 코드 예시 포함. 협의 안건·검증·관련 문서. `ai-h2-auth-middleware.md` 와 같은 패턴
- **2026-05-25 (갱신 8)**: §11 의 *암묵적 누락* 보강:
  - **분기 2.A.ET 신설** — 세션 종료 trigger 경로. ET-A (클라가 AI 에 직접 신호) 추천. ET-B (Spring 경유) 는 hop 추가·신규 RPC 채널 필요로 거부, ET-C (timeout) 는 비결정적으로 거부. 클라는 종료 시 Spring (ended_at) + AI (batch 시작) 양쪽에 통보
  - **§11.1.A 페르소나 경로 명시** — 토큰의 사용자 정보로 Spring 이 자동 필터링. 클라는 persona 파라미터 전달 불필요. 사용자 페르소나는 `users.persona` 컬럼 (12-persona-difficulty.md)
  - **§11.3 schema migration 작업 추가** — `exercise_feedback_templates`, `session_feedback_logs`, `user_preferences (tts_*)`, `users.persona` 4종 Flyway migration 명시. `TtsPreferenceController`, `SessionController.end` 도 추가. 총 230~290줄로 정정
  - **선행 작업 명시** — `POST /sessions` (BE-01) 와 `users.persona` 컬럼이 미구현 시 TTS 작업 전 선행 필요
- **2026-05-25 (갱신 3)**: 합성 방식 분기 8 신설 + 평가 가치 우선순위 §1.4 명문화 (사용자 명시: 돈 > 끊기지 않음 > 인지):
  - 8-A (Device TTS) MVP 채택 — 가치 1·2 압도적 만족 (돈 0원, 오프라인). 가치 3 (인지) 은 짧은 명령형 멘트에 충분
  - 8-D (클라우드 + 캐싱) 는 격상 후보 — 사용자 피드백 트리거(§8.4) 발동 시 1~2일 작업으로 전환
  - 8-C·8-G 즉시 탈락 (가치 2 hard requirement 위배)
  - 8-B·8-E·8-F 는 가치 1 또는 가치 3 에서 8-A 대비 명확 우위 없음
  - 분기 1~7 추천은 가치 우선순위 재검토 후 변경 없음 (영향 없는 분기들)
- **결정 후 후속 작업**:
  - `11-tts-youtube-guide.md` 정정: "발화 이벤트 로그" → "피드백 판정 이벤트 로그", `GOOD_FORM` 예시 제거, 쓰로틀 코드 예시 단순화
  - `23-ai-tasks-detail.md` 에 AI 변경 비용 표의 작업 항목 신설

---

## 관련 문서

- [`../REQUIREMENTS.md`](../REQUIREMENTS.md) §5·6·8 — 이 문서의 요구사항 근거
- [`../11-tts-youtube-guide.md`](../11-tts-youtube-guide.md) — 현행 가이드 (결정 후 정정 대상)
- [`../05-database-design.md`](../05-database-design.md) — `exercise_feedback_templates`, `session_feedback_logs`
- [`./ai-backend-coupling.md`](./ai-backend-coupling.md) — AI 변경 최소화 원칙 (분기 1 의 정당화 근거)
- [`../tasks/22-backend-tasks-detail.md`](../tasks/22-backend-tasks-detail.md) — BE-10·11·12
- [`../tasks/23-ai-tasks-detail.md`](../tasks/23-ai-tasks-detail.md) — 결정 후 AI 작업 항목 추가 대상
