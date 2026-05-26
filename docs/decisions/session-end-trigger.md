# Decision: 세션 종료 신호 trigger 재검토 (ET-A vs ET-B)

상태: **✅ CLOSED — ET-H 채택 (2026-05-26)**
작성: 2026-05-26
배경 문서: [`./tts-design.md`](./tts-design.md) §2.A.ET (2026-05-25 결정 박스), [`../REQUIREMENTS.md`](../REQUIREMENTS.md)
연관: [`./tts-design.md`](./tts-design.md) 2026-05-26 gRPC 통일 결정 박스

> **✅ 결정 (2026-05-26)**: 옵션 B (ET-H, Spring 단일 분배자 패턴) 채택. 사유는 §5 권고와 동일 — ET-A 의 차별화 장점이 분석 결과 실재하지 않고, ET-B/ET-H 가 코드·문서·운영 모든 축에서 단순.
>
> **박힌 코드 (2026-05-26)**:
> - `ExercisesController.stopSession` (`PUT /exercises/sessions/{id}/stop`) **삭제**
> - `SessionService.endSession`: endTime 기록 + `TransactionSynchronization.afterCommit` 안에서 `analysisService.stopAnalysis(sessionId)` 호출
> - `ExerciseAnalysisService.stopAnalysis` javadoc: "사용자 강제 중단" → "afterCommit 콜백" 정정
> - `SessionController` javadoc: "ET-A" → "ET-H" 정정
> - `tts-design.md §2.A.ET` 결정 박스 갱신
> - `handoff/ai-tts-feedback-batch.md §2.F` 정정

---

## 1. 왜 재검토?

`tts-design.md` §2.A.ET 가 2026-05-25 에 **ET-A 확정** 으로 박혀 있으나, 이후 두 가지 사실이 결정 전제를 바꾸었음:

1. **2026-05-26 gRPC 통일 결정** — feedback batch 송신이 REST → gRPC `ReportFeedbackBatch` 로 통일됨. ET-A 추천 사유 ③ ("AI 가 batch 송신 주체이므로 trigger 도 AI 가 직접 받는 게 책임 일관") 의 *책임 일관* 논거가 약해짐. 어차피 gRPC 한 채널로 흐르면 누가 trigger 받든 통신 경로 비용은 같음.

2. **실제 코드는 ET-B 절반 + ET-A 절반 공존** — 클라 미구현 상태에서 백엔드만 양쪽 endpoint 가 박혀 있음. 어느 쪽으로 정리하든 *클라 마이그레이션 부담 0* (= 기존 결정의 큰 비용 요소 소멸).

위 두 변경으로, 분기를 *원점에서 재평가* 할 가치가 생김.

---

## 2. 현재 코드 실태 (2026-05-26 기준)

### 백엔드: 두 endpoint 가 공존

| endpoint | 위치 | 동작 | ET 분류 |
|---|---|---|---|
| `PATCH /sessions/{id}/end` | `SessionController.java:25–32` → `SessionService.endSession()` (`SessionService.java:122–137`) | Session.endTime 기록만. AI 호출 **없음** | ET-A 의 *Spring 절반* |
| `PUT /exercises/sessions/{id}/stop` | `ExercisesController.java:89–94` → `ExerciseAnalysisService.stopAnalysis()` (`ExerciseAnalysisService.java:172–191`) | gRPC `StopAnalysis` → AI. AI 가 누적 결과로 `CompleteAnalysis` 콜백 (endTime 포함) | ET-B 흐름 (클라→Spring→AI) |
| `PUT /exercises/sessions/{id}/complete` | `ExercisesController.java:107–126` | `@Deprecated` — 옛 흐름 (프론트가 자체 카운트한 결과 직접 반영) | — |

### AI 측

- `exercise_servicer.py:98–122` `StopAnalysis` 핸들러 — Spring 으로부터 gRPC 수신 시 누적 rep 으로 `CompleteAnalysis` 콜백. 즉 **AI 는 Spring 으로부터만 종료 신호를 받게 구현됨**. 클라 직접 호출용 endpoint **없음**.

### 프론트엔드

- `frontend/services/` 안에 세션 종료 관련 호출 코드 **전무**. *어느 endpoint 도 아직 호출하지 않음*.

### 모순

- `SessionController` javadoc 은 ET-A 라고 명시했으나 정작 AI 호출이 없음 — 주석과 동작 불일치
- 두 endpoint 중 클라가 어느 것을 부르라는 명세가 어디에도 없음
- ET-A 가 doc 결정인데 코드는 ET-B 가 90% 완성 + ET-A 가 50% 완성

---

## 3. 옵션 정의 (재정의)

### ET-A. 클라가 Spring + AI 양쪽 직접 호출

```
사용자 "운동 종료" 버튼
      │
      ├─ PATCH /sessions/{id}/end     → Spring (endTime 기록)
      └─ gRPC ? or HTTP ?             → AI   (BT-SET final batch trigger)
                                            └─ gRPC ReportFeedbackBatch → Spring
```

**필요 변경**:
- AI 측: 클라 직접 호출용 endpoint 신설 (HTTP `POST /sessions/{id}/end` 가 자연 — 프론트에서 gRPC 어려움). **신규 endpoint 1개**
- Spring 측: `ExercisesController.stopSession` + `ExerciseAnalysisService.stopAnalysis` + AI `StopAnalysis` gRPC 핸들러 — **모두 삭제 또는 internal-only 로 격리**
- 클라: 2 endpoint 호출 (병렬), 부분 실패 허용 처리

### ET-B. 클라 → Spring → AI

```
사용자 "운동 종료" 버튼
      │
      └─ PATCH /sessions/{id}/end     → Spring
                                          ├─ Session.endTime 기록
                                          └─ gRPC StopAnalysis → AI
                                                                └─ gRPC ReportFeedbackBatch → Spring
                                                                └─ gRPC CompleteAnalysis → Spring (덮어쓰기)
```

**필요 변경**:
- Spring 측: `SessionController.endSession` 안에서 `analysisService.stopAnalysis(sessionId)` 호출 1줄 추가 (양쪽 책임 통합). `ExercisesController.stopSession` endpoint **삭제** (중복 제거)
- AI 측: **변경 없음** (이미 `StopAnalysis` 핸들러 존재)
- 클라: 1 endpoint 호출만

### ET-H. 하이브리드 — 클라는 Spring 만, Spring 이 단일 책임 분배

ET-B 와 사실상 같음. 차이는 *endpoint 1개* 라는 명시적 합의:
- `PATCH /sessions/{id}/end` 만 살리고 `PUT /exercises/sessions/{id}/stop` 폐기
- Spring 이 endTime 기록 + AI gRPC 호출 + 결과 콜백 대기 (또는 fire-and-forget)

---

## 4. 비교

가치 우선순위는 `tts-design.md §1.4` (돈 > 운동 중 끊기지 않음 > 인지) 에서, **세션 종료 시점이므로 #2 는 무관**. 진짜 기준은 *부분 실패 허용성*, *코드 변경량*, *책임 명료성*.

| 항목 | ET-A | ET-B / ET-H |
|---|---|---|
| 클라 호출 수 | 2개 (병렬) | 1개 |
| 부분 실패 허용 | ✅ 한쪽 다운돼도 다른 쪽 진행 | ❌ Spring 다운 = AI 도 신호 못 받음 |
| Spring 코드 변경 | 삭제 큼 (`ExercisesController.stopSession` + `ExerciseAnalysisService.stopAnalysis`) | 1줄 추가 (`SessionService.endSession` 안에 stopAnalysis 호출) + 중복 endpoint 1개 삭제 |
| AI 코드 변경 | endpoint 신설 1개 ([[feedback-minimize-python-changes]] 위반) | 없음 |
| 클라 구현 부담 | 2개 endpoint 핸들링, 병렬 호출 + 부분 실패 처리 | 1 endpoint 핸들링 |
| 책임 모델 | 분산 (Spring 은 endTime, AI 는 batch) | 중앙집중 (Spring 이 분배자) |
| gRPC 통일 후 정합 | 약해짐 (AI HTTP endpoint 추가하면 채널 분산) | 강해짐 (모든 internal 호출 gRPC) |
| 코드↔doc 일치 정리 | 코드 큰 변경 필요 | 코드 거의 그대로, doc 만 변경 |
| 강제 종료 (앱 죽음·네트워크 끊김) safety net | AI 측 timeout 필요 | Spring 측 scheduler 이미 존재 (`SessionTimeoutScheduler`) — 그대로 활용 가능 |

### 부분 실패 시나리오 자세히

| 시나리오 | ET-A | ET-B |
|---|---|---|
| Spring 1초 다운 | AI 는 종료 신호 받음 → batch 송신 OK. endTime 만 누락 (재시도로 복구) | AI 는 종료 신호 못 받음 → batch 미송신. `SessionTimeoutScheduler` 가 결국 FAILED 로 처리. **batch 데이터 영구 손실** |
| AI 1초 다운 | Spring 은 endTime 기록 OK. AI batch 송신 안 됨 (다음 호출 시 재시도 가능?) | Spring 이 gRPC StopAnalysis 실패 응답 받음 → 재시도 큐 또는 timeout 스케줄러 의존 |
| 둘 다 다운 | 클라 양쪽 실패 → 사용자 화면에 경고. 다음 접속 시 클라 재시도 가능 | 클라 endpoint 실패 → 사용자 화면에 경고. 다음 접속 시 재시도 |

ET-A 의 **유일한 진짜 장점은 *Spring 다운 시 AI batch 손실 방지*** — 단 이는 [`SessionTimeoutScheduler`](../../backend/src/main/java/com/shadowfit/service/Exercise/SessionTimeoutScheduler.java) 와 AI 측 재시도 큐로 ET-B 에서도 보강 가능.

---

## 5. 추천 — **ET-B (또는 ET-H 정리)**

### 사유

1. **gRPC 통일 정합** — 2026-05-26 결정으로 internal 통신은 전부 gRPC 가 됨. ET-A 면 클라→AI 만 HTTP 라 *internal 통신 채널 또 분산*. ET-A 의 책임 일관 논거가 ET-B 로 역전됨
2. **[[feedback-minimize-python-changes]]** — ET-A 는 AI endpoint 신설이 필요. ET-B 는 AI 변경 0
3. **코드↔doc 정리량 최소** — 현재 코드가 이미 90% ET-B 형태. ET-A 로 가면 큰 삭제·재구성 필요
4. **단일 책임 endpoint** — `SessionController` javadoc 의 ET-A 표기를 *코드와 일치* 시키는 정리 + `ExercisesController.stopSession` 중복 endpoint 폐기로 명세 단순화
5. **safety net 이미 존재** — `SessionTimeoutScheduler` 가 IN_PROGRESS 세션을 FAILED 로 정리하는 메커니즘 이미 작동 중 → 강제 종료·부분 실패 케이스 부분 보완
6. **클라 단순화** — 클라가 부분 실패 처리 안 해도 됨. 1 endpoint 호출 후 응답만 보면 됨

### 감수

- **Spring 단일 장애 시 AI batch 손실 가능성** — 단 AI 측 재시도 큐 (이미 BT-SET retry 0/5/15/35s 로 설계됨, handoff 참조) + Spring 측 scheduler 결합으로 거의 0 에 근사. 운영 진입 시 디스크 영속화 (옵션 C) 결합하면 손실 0
- **단일 Spring 책임 ↑** — `SessionService.endSession` 안에 gRPC 호출이 끼는데, transactional 경계 안에서 외부 호출은 *bad pattern* → 호출은 **transaction commit 이후 fire-and-forget** 으로 분리 필요 (구현 시 주의)
- **ET-A 도 doc 박혀 있던 결정** 이라 정정 표기 필요. 단 *전제 변경* (gRPC 통일) 이 명시적이라 정당화 가능

### ET-H (ET-B 정리 버전) 구체안

```
endpoint: PATCH /sessions/{id}/end        (유일)
└─ SessionController.endSession()
   └─ SessionService.endSession()
      ├─ Session.endTime 기록 (현재 그대로)
      └─ TransactionSynchronization afterCommit:
         └─ exerciseAnalysisService.stopAnalysis(sessionId)
            └─ gRPC StopAnalysis → AI (이미 구현됨)
               └─ AI: state.on_session_end() (BT-SET final batch)
               └─ AI: CompleteAnalysis 콜백 (이미 구현됨)
```

**삭제**: `ExercisesController.stopSession` + 관련 @PutMapping 라우트 (`SessionController` 가 합병)

**변경 라인 수 (Spring)**: +5 (TransactionSynchronization 호출) / -15 (`stopSession` controller 삭제) = 순 감소

---

## 6. 결정 옵션 (사용자 선택)

| 옵션 | 의미 |
|---|---|
| **A. ET-A 채택 (기존 doc 결정 유지)** | 코드 큰 재구성. AI endpoint 신설. [[feedback-minimize-python-changes]] 위반. doc 재확인만 필요 |
| **B. ET-B / ET-H 채택 (재검토 결과 권고)** | 코드 거의 그대로. AI 변경 0. doc §2.A.ET 결정 박스 정정. `ExercisesController.stopSession` 폐기 |
| C. 현 상태 (양쪽 공존) 유지 | 권고 안 함. 명세 모호로 클라 구현 시 혼란 |

본 문서는 [[feedback-decision-doc]] 따른 분석 문서. 결정은 사용자 ([[feedback-user-decides-not-claude]]).

---

## 7. 결정 후 작업 (참고)

**옵션 B 선택 시**:
- `ExercisesController.stopSession` + 관련 javadoc 삭제
- `SessionService.endSession` 에 `TransactionSynchronizationManager.registerSynchronization` afterCommit 으로 `analysisService.stopAnalysis(sessionId)` 호출
- `ExerciseAnalysisService.stopAnalysis` 의 javadoc "사용자 강제 중단" → "Spring afterCommit 으로 AI 통보" 정정
- `SessionController.endSession` javadoc 의 "ET-A" 표기 → "ET-H (Spring 분배)" 정정
- `tts-design.md §2.A.ET` 결정 박스 무효화 + 본 문서 참조
- `handoff/ai-tts-feedback-batch.md §2.F` 의 ET-A 언급 정정
- AI 측 `StopAnalysis` 핸들러는 그대로 (handoff 가이드 그대로 유효)

**옵션 A 선택 시**:
- AI 측: HTTP `POST /sessions/{id}/end` endpoint 신설 (handoff md 갱신 필요)
- Spring 측: `ExercisesController.stopSession` + `ExerciseAnalysisService.stopAnalysis` + `ExerciseGrpcService` 의 stub-side StopAnalysis 클라이언트 코드 삭제 (Spring→AI gRPC 의 StopAnalysis 라인 정리)
- AI 측 `StopAnalysis` gRPC 핸들러 — Spring 안 부르므로 사실상 dead 코드, 삭제 (또는 internal admin 용으로 유지)
- 클라: 2 endpoint 병렬 호출 + 부분 실패 핸들링 명세
