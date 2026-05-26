# TTS 피드백 — 구현 전 협의 체크리스트

마지막 업데이트: 2026-05-26 (gRPC 통일 + ET-H 결정 박제)
배경: [`../decisions/tts-design.md`](../decisions/tts-design.md) §12 — 8-A 채택 후 구현 시작 전 Front/AI/Spring 3자가 합의해야 할 경계 계약 28건
연관: [`./ai-tts-feedback-batch.md`](./ai-tts-feedback-batch.md) — AI 측 작업 요청서, [`../decisions/session-end-trigger.md`](../decisions/session-end-trigger.md) — ET 분기 재검토

> **✅ 2026-05-26 일괄 갱신**: 두 결정 (gRPC 통일 + ET-H) 이 다수 안건을 *해소*. 영향 안건 11건:
>
> - **gRPC 통일로 해소** — #3 (proto schema 강제로 payload 합의 불필요), #5 (REST `/internal/*` 자체 소멸), #4 (proto `feedback_type` 양쪽 박힘), #20 (proto `FeedbackBatchResponse` 박힘), #22 (gRPC deadline 으로 변경 — httpx timeout 무관), #23 (gRPC `Authorization: Bearer` 단일화 — 회전 정책만 잔존)
> - **ET-H 채택으로 해소** — #6 (AI 신규 endpoint 불필요), #7 (클라 1 endpoint 호출만)
> - **이미 구현 완료로 해소** — #10 (멱등성 박힘), #11 (proto 통일로 부분 실패 없음 — 전체 batch 1 응답)
>
> 잔여 협의 안건: #1, #2, #8, #9, #12, #13, #15, #17~19, #21, #24~28 (16건). 표는 원형 유지 (당시 합의 맥락 보존) + 해소 안건은 *상태* 컬럼에 `✅ 2026-05-26 해소` 표시. 잔여 안건 중심으로 진행할 것.

---

## 개요

§10·§11 의 데이터 플로우·책임이 정해졌더라도, *경계 계약* (API schema, 동작 정책) 은 사전 합의 필수. 합의 누락 시 통합 단계 재작업.

본 체크리스트는 **작업 진행용**. 각 항목 합의 완료 시 *상태* 컬럼에 ✅ 또는 결정 사항 기록.

당사자 약어:
- **F**: Front (React Native)
- **A**: AI server (FastAPI)
- **S**: Spring
- **3자**: F + A + S 모두

---

## 📊 현재 결정 상태 종합 (2026-05-25)

### ✅ 사용자 명시적 confirm (7건)

| 위치 | 결정 |
|---|---|
| 분기 3 (rep 다중 결함) | priority 최솟값 1개 + RC-2 (5배수 카운트 발화) |
| 분기 6 (GOOD_FORM) | 발화·송신 안 함 |
| 분기 9 (영상 audio ducking) | `expo-av setAudioModeAsync` (9-1) |
| **분기 2.A.ET (세션 종료 trigger)** | **ET-H (재검토 2026-05-26): Spring 단일 분배자. 클라는 `PATCH /sessions/{id}/end` 한 번만, Spring 이 afterCommit 으로 gRPC `StopAnalysis` → AI. ~~ET-A~~ 폐기. safety net 은 `SessionTimeoutScheduler` 이미 존재** |
| #25 (priority 응답 메타) | 포함 유지 (현 코드) |
| #16 (시간대 형식) | 서버 Asia/Seoul 고정 + API JSON 마커 없음 + DB LocalDateTime + UI KST |
| #14 (ttsSpeed 검증) | UI 슬라이더 + Spring `@DecimalMin/@DecimalMax` 표준 검증 |

### 🔵 보류 (Front UI 디자인 확정 후 재검토, 1건)

| # | 추천 (보류) |
|:-:|---|
| #15 TTS preferences 즉시 효과 | cached value / 다음 rep 부터 반영 (운동 중 변경 UI 없음 가정) — Front UI 결정 후 |

### 📝 추천 박제 — 분기 결정 (확정 마크 미완, 9건)

작업 시작은 가능하나 *공식 ✅ 미마크*:

| 분기 | 추천 | 갱신 |
|---|---|:-:|
| 분기 1 | 1-B (AI 가 8종 분류) | - |
| 분기 2 | 2-A (AI 가 batch 송신) | - |
| ~~분기 2.A.ET~~ | ~~ET-A~~ → ET-H (재검토 2026-05-26, 위 confirm 표 참조) |
| **분기 2.A.BT** | **BT-SET (세트 경계 + 세션 종료 final)** | 갱신 13·14 |
| 분기 4 | 4-A (운동 진입 시 1회 캐시) | - |
| 분기 7 | 7-1 (HTTP response 확장) | - |
| 분기 8 | 8-A (`expo-speech` OS TTS) + 8-D 격상 | 갱신 3 |
| §갱신 12 | snake_case payload + Spring `@JsonNaming` | 갱신 12 |

### ⚠️ OPEN — 명시적 confirm 미완

| 분기 | 사유 |
|---|---|
| **분기 5 (TTS off fallback)** | 사용자 답변이 질문 의도와 어긋남. *언어 한국어 확정* 만, *off 시 자막+진동* 안 확정 |

### ☐ 미결정 — 체크리스트 28건 중

```
🔴 최우선 5건:    #1, #2, #3, #4, #5         (3자 미팅 1회로 일괄 가능)
🟡 중요 10건:     #6~#15 (#10, #14·15 제외)
🟢 차순위 13건:   #16~#28 (#25 제외)
                  ─────
                  미결정 22건
```

### 🔵 MVP 보류 권장 (7건)

```
#8  분류 임계값 위치 (AI 단독, 작업 시 결정)
#9  priority 상수 위치 (분기 3-A-1 채택으로 사실상 끝)
#11 batch 부분 실패 (운영 진입 후)
#18 events 페이징 (MVP 미도입)
#21 batch 크기 한도 (운영 후)
#22 batch 타임아웃 (10초 가정 충분)
#23 내부 토큰 관리·회전 (환경변수 고정 충분)
```

### 진짜 협의 필요 (4건)

| # | 안건 | 누구와 | 시점 |
|:-:|---|---|---|
| #7 | 클라 양방향 호출 순서 | Front | BE-14 시작 전 |
| #13 | 페르소나 변경 후 캐시 무효화 | Front | BE-13 작업 중 |
| #17 | summary 집계 단위 | Front | BE-15 작업 중 |
| #26 | 종료 신호 safety net | Front | 베타 진입 전 |

### 코드 상태로 *사실상 결정* (2건)

| # | 코드 상태 |
|:-:|---|
| #12 templates 응답 구조 | 이미 Array (`List<FeedbackTemplateDto>`) — 변경 안 하면 결정 |
| #19 트레이너 권한 | `UserRole` 에 `TRAINER` 없음 → 1학기 보류 자연 |

### 작업 시작 가능 여부

| 작업 | 차단 요소 | 시작 가능? |
|---|---|---|
| BE-13 (페르소나 + BT-SET) | #1·#2·#3 (추천 박제) | ⭕ 추천대로 가능 |
| BE-14 (Session 종료) | #5·#7 (추천 + 1:1) | ⭕ 추천대로 가능 |
| BE-15 (피드백 조회) | #17 (진짜 결정) | △ #17 결정 후 |
| AI handoff | #3·#4·#6·#10 (추천 박제) | ⭕ 추천대로 가능 |

---

---

## 🔴 최우선 — 통합 전 반드시 합의

| # | 안건 | 관련 Spring API / 인터페이스 | 결정 옵션 (추천) | 당사자 | 상태 |
|:-:|---|---|---|:-:|:-:|
| 1 | 8종 enum 표기 | gRPC `ReportFeedbackBatch`, `GET /sessions/{id}/feedbacks`, `GET /sessions/{id}/feedback-summary`, `GET /exercises/{id}/feedback-templates`, proto `FeedbackEvent.feedback_type` | **추천**: `KNEE_OUT` UPPER_SNAKE / master = `REQUIREMENTS.md` §6 (코드 `FeedbackType.java` 이미 일치, 문서 명시만) | 3자 | ☐ |
| 2 | 페르소나 enum 표기 | `GET /users/me`, `PATCH /users/me/persona`, `GET /exercises/{id}/feedback-templates` | **추천**: `BEGINNER/ADVANCED/DIET/REHAB` / master = `12-persona-difficulty.md` (`Member.selectedPersona` 와 정합) | 3자 | ☐ |
| 3 | batch payload schema | ~~`POST /internal/feedback/batch`~~ → gRPC `ReportFeedbackBatch` | proto `FeedbackBatchRequest{session_id, set_no, is_final, events:[FeedbackEvent]}` 가 schema 강제. snake_case 자동 (proto 기본). DTO `@JsonNaming` 불필요 | A ↔ S | ✅ 2026-05-26 해소 |
| 4 | proto `feedback_type` 필드 | `ai-server/app/proto/exercise.proto` + `backend/src/main/proto/exercise.proto`, `POST /pose` 응답 모델 | proto `FeedbackEvent.feedback_type = 1` (string) 양쪽 동기 박힘. AI 측 화이트리스트 검증은 `classify_rep` 함수 내장 | A ↔ S | ✅ 2026-05-26 해소 |
| 5 | 인증·토큰 endpoint 분리 | ~~`/api/*` (JWT) vs `/internal/*` (`X-Internal-Token`)~~ | gRPC 통일로 `/internal/*` REST 자체 소멸. 클라↔Spring 전부 JWT, AI↔Spring 전부 gRPC `Authorization: Bearer` (`InternalAuthInterceptor`) | 3자 | ✅ 2026-05-26 해소 |

---

## 🟡 중요 — 구현 중 결정

| # | 안건 | 관련 Spring API / 인터페이스 | 결정 옵션 (추천) | 당사자 | 상태 |
|:-:|---|---|---|:-:|:-:|
| 6 | 세션 종료 신호 형식 (~~ET-A~~ → ET-H) | Spring 이 분배자 — 클라는 `PATCH /sessions/{id}/end` 만, Spring afterCommit 으로 gRPC `StopAnalysis` → AI | ET-H 채택 (분기 2.A.ET 재검토, 2026-05-26). AI 신규 endpoint 불필요 — 기존 `StopAnalysis` 핸들러 그대로 사용 | A ↔ F | ✅ 2026-05-26 해소 |
| 7 | 클라 양방향 호출 순서 | ~~Spring + AI 양쪽~~ → Spring 1 endpoint 만 | ET-H 채택으로 클라는 `PATCH /sessions/{id}/end` 단일 호출. 부분 실패 처리 불필요 | F ↔ S | ✅ 2026-05-26 해소 |
| 8 | 분류 임계값 위치 | (Spring API 없음 — AI 내부) | **추천**: `squat_analyzer` 상수 + 영상 5~10건 튜닝 | A 단독 (공유) | ☐ |
| 9 | priority 상수 위치 | (Spring API 없음 또는 `GET /exercises/{id}/feedback-templates` 응답에 포함) | **추천**: AI 내장 (3-A-1) — 3-A-2 거부 | A 단독 | ☐ |
| 10 | batch 재시도·멱등성 | gRPC `ReportFeedbackBatch` | Spring 측 `(session_id, occurred_at, feedback_type)` uniqueKey + `INSERT IGNORE` 박힘. AI 측 retry 백오프는 handoff §2.E 가이드에 명시 | A ↔ S | ✅ 2026-05-26 해소 |
| 11 | batch 부분 실패 처리 | gRPC `ReportFeedbackBatch` | proto 통일로 *전체 batch 1 응답* — `saved_count` 만 반환 (멱등 중복 제외). invalid `feedback_type` 1건이라도 있으면 전체 reject (`INVALID_ARGUMENT`) → AI 가 재송신 | A ↔ S | ✅ 2026-05-26 해소 |
| 12 | templates 응답 구조 | `GET /exercises/{id}/feedback-templates` | **추천**: `[{feedbackType, message}]` Array (정렬·확장 유리) | F ↔ S | ☐ |
| 13 | 페르소나 변경 후 캐시 무효화 | `PATCH /users/me/persona` → `GET /exercises/{id}/feedback-templates` 재호출 | PATCH 응답에 `templatesReloadRequired: true` vs 클라가 운동 시작 시 항상 재호출 — *Front 의 캐시 정책* 합의 필요 | F ↔ S | ☐ |
| 14 | `ttsSpeed` 검증 | `PATCH /preferences/tts` | **✅ 결정 (2026-05-25)**: UI 슬라이더로 0.5~2.0 범위 강제 + Spring 표준 `@DecimalMin("0.5") @DecimalMax("2.0")` 검증 어노테이션 (방어용). UI 가 범위 보장 → 평시 발동 X, 비정상 호출만 422 응답 | F ↔ S | ✅ |
| 15 | TTS preferences 즉시 효과 | `GET /preferences/tts`, `PATCH /preferences/tts` | **추천**: 클라 cached value / 다음 rep 부터 반영 (운동 중 변경 UI 없음 가정) — 단 *Front UI 디자인* 확정 후 재검토 필요 | F ↔ S | 🔵 보류 |

---

## 🟢 차순위 — 구현 후 보완

| # | 안건 | 관련 Spring API / 인터페이스 | 결정 옵션 | 당사자 | 상태 |
|:-:|---|---|---|:-:|:-:|
| 16 | 시간대 형식 | 시각 필드 포함 모든 API — gRPC `ReportFeedbackBatch` (proto `Timestamp` Asia/Seoul 변환), `GET /sessions/{id}/feedbacks`, `PATCH /sessions/{id}/end`, `GET /sessions/{id}/feedback-summary` | **✅ 결정 (2026-05-25)**: 한국 전용 서비스 정합. (1) 서버 timezone Asia/Seoul 고정 (Spring `spring.jackson.time-zone: Asia/Seoul` + AI `TZ=Asia/Seoul`), (2) API JSON 형식 마커 없음 (`"2026-05-25T10:23:45"`), (3) DB `LocalDateTime` 유지, (4) UI KST 표시. gRPC proto `Timestamp` → Asia/Seoul `LocalDateTime` 변환은 `FeedbackLogService` 안에서 처리. 글로벌 진출 시 재검토 | 3자 | ✅ |
| 17 | summary 집계 단위 | `GET /sessions/{id}/feedback-summary` | `feedback_type` 별 카운트 + sync_rate avg/min/max | F ↔ S | ☐ |
| 18 | events 페이징 | `GET /sessions/{id}/feedbacks` | `page/size` (max ~210건 가능) | F ↔ S | ☐ |
| 19 | 트레이너 권한 헤더 | `GET /sessions/{id}/feedbacks`, `GET /sessions/{id}/feedback-summary` | 기존 권한 모듈 재사용 | S 단독 | ☐ |
| 20 | batch 응답 형식 | gRPC `ReportFeedbackBatch` | proto `FeedbackBatchResponse{session_id, saved_count}` 박힘 | A ↔ S | ✅ 2026-05-26 해소 |
| 21 | batch 크기 한도 | gRPC `ReportFeedbackBatch` | gRPC 기본 max message size 4MB. events ~수천 건까지 안전 (FeedbackEvent ~50 bytes). 분할 정책 일단 불필요 — 베타 진입 후 측정 | A ↔ S | 🟢 자연 해소 |
| 22 | batch 타임아웃 | gRPC `ReportFeedbackBatch` (deadline) | gRPC deadline ~10초 권장. AI 측 httpx 사라짐 | A ↔ S | ✅ 2026-05-26 해소 |
| 23 | 내부 토큰 관리·회전 | gRPC `Authorization: Bearer` metadata (`InternalAuthInterceptor`) | 채널 단일화로 토큰도 단일. 환경변수 → secret manager 단계적 / 회전 정책은 미정 (잔여) | S 단독 | 🟡 단순화 — 잔여 |
| 24 | 빈 결과 처리 | `GET /exercises/{id}/feedback-templates` | 페르소나 row 없으면 BEGINNER fallback vs 404 | F ↔ S | ☐ |
| 25 | enum 응답 메타 (priority) | `GET /exercises/{id}/feedback-templates` | **✅ 결정 (2026-05-25)**: priority 응답 포함 유지 (현 코드 상태). 변경 0, 응답 부담 무시 수준 (~16 byte), 미래 활용 (Front 별표·색 표시) 가능성 보존 | F ↔ S | ✅ |
| 26 | 종료 신호 safety net | (AI 측 종료 처리, Spring API 무관) | 클라 신호 누락 시 N분 timeout 으로 batch 송신 (ET-C 미니멀 도입) | A ↔ F | ☐ |
| 27 | 세션 메모리 정리 | (Spring API 없음) | batch 송신 성공 후 즉시 누적 데이터 삭제 | A 단독 | ☐ |
| 28 | enum 추가 시 배포 순서 | 모든 enum 사용 API | Spring 먼저 (DB seed + enum) → AI → Front | 3자 | ☐ |

---

## 각 안건 상세 설명

위 표의 "안건" 컬럼이 짧아 *무엇이 문제인지* 빠르게 안 보일 수 있어 간략 설명.

### 🔴 최우선

**#1 8종 enum 표기** — 자세 결함 분류 enum (`KNEE_OUT`, `KNEE_IN`, `HIP_HIGH`, `BACK_BENT` 등 8종) 의 *표기·정의 master 위치*. proto / Spring `FeedbackType.java` / Python 상수 / Front 문자열 / DB seed **5곳** 이 일치해야 하므로 master 가 어디인지 명시 안 하면 drift 발생.

**#2 페르소나 enum 표기** — 사용자 페르소나 (`BEGINNER`/`ADVANCED`/`DIET`/`REHAB`) 의 *master 위치*. `Member.selectedPersona` 와 `12-persona-difficulty.md` 가 정합한지 확인 + 문서·코드 어느 쪽이 master 인지 명시.

**#3 batch payload schema** — ✅ **2026-05-26 해소**. REST → gRPC `ReportFeedbackBatch` 통일로 proto `FeedbackBatchRequest` 가 schema 강제. DTO snake_case 어노테이션 불필요 (proto 기본 snake_case). 이전 합의안 (Spring DTO `@JsonNaming` + `set_no`/`is_final` 필드) 은 proto 메시지로 실현.

**#4 proto `feedback_type` 필드** — ✅ **2026-05-26 해소**. proto `FeedbackEvent.feedback_type = 1` (string) 으로 양쪽 동기 박힘. AI 측 화이트리스트 검증은 `classify_rep` 함수 내장. `RepCompletedEvent` 라는 이름 대신 `FeedbackEvent` 로 정착.

**#5 인증·토큰 endpoint 분리** — ✅ **2026-05-26 해소**. gRPC 통일로 `/internal/*` REST 자체 소멸. 클라↔Spring 전부 JWT, AI↔Spring 전부 gRPC `Authorization: Bearer` (`InternalAuthInterceptor`).

### 🟡 중요

**#6 (AI 측) 세션 종료 신호 형식** — ✅ **2026-05-26 해소**. ET-H 채택으로 Spring 이 단일 분배자. 클라는 `PATCH /sessions/{id}/end` 만, Spring 이 afterCommit 으로 gRPC `StopAnalysis` → AI. AI 측 신규 endpoint 불필요 — 기존 `StopAnalysis` 핸들러 (`exercise_servicer.py:98`) 에 `state.on_session_end()` 1줄만 추가하면 됨 (handoff §2.F 참조).

**#7 클라 양방향 호출 순서** — ✅ **2026-05-26 해소**. ET-H 로 클라는 단일 호출만. 부분 실패 처리 정책 불필요.

**#8 분류 임계값 위치** — `KNEE_OUT` 판정 임계값 (예: 무릎 거리 비율 > 1.2) 을 어디에 두는가. `squat_analyzer.py` 코드 상수 / config 파일 / DB. 영상 5~10건 튜닝 책임 주체도 포함.

**#9 priority 상수 위치** — 다중 결함 검출 시 1개 선택용 priority. AI 가 *내장 상수* (3-A-1) 로 갖는가, Spring 부팅 시 `GET /exercises/{id}/feedback-templates` 호출로 fetch 하는가 (3-A-2).

**#10 batch 재시도·멱등성** — **분기 2.A.BT (BT-SET) 채택으로 필수**. 세트 경계마다 batch 송신 + 휴식 시간 (30~90s) 활용 retry → 같은 events 가 *합법적으로* 재송신될 수 있음. AI 측 backoff (0s/5s/15s/35s, 4회). Spring 측 `session_feedback_logs` 에 `(session_id, occurred_at, feedback_type)` uniqueKey 추가 + `INSERT IGNORE` 또는 `ON DUPLICATE KEY UPDATE` 로 중복 흡수. 현재 uniqueKey 없음 → BE-13 schema 변경 시 같이 처리.

**#11 batch 부분 실패** — events 배열 중 일부가 invalid (잘못된 `feedbackType` 등) 일 때 *전체 reject* vs *유효한 것만 insert + reject 목록 응답*.

**#12 templates 응답 구조** — `GET /exercises/{id}/feedback-templates` 응답 형태. `{"KNEE_OUT": "무릎이…"}` Map vs `[{type, message, priority}]` Array. 클라 캐시 구조와 정합.

**#13 페르소나 변경 후 캐시 무효화** — 사용자가 `PATCH /users/me/persona` 로 페르소나 바꾼 후 클라의 templateCache 가 옛 값을 갖는 문제. PATCH 응답에 신호 vs 운동 시작마다 재호출.

**#14 `ttsSpeed` 검증** — 클라가 0.5~2.0 범위 벗어난 값 보낼 때 처리. Spring 이 422 에러 응답 vs 자동 클램프 (1.0 이하·이상 자르기).

**#15 TTS preferences 즉시 효과** — 사용자가 운동 중 TTS 설정 변경 시 *현재 세션의 다음 rep 부터 반영* vs *다음 세션부터*. 클라가 cached value 사용 vs 매 발화 fresh fetch.

**#16 시간대 형식** — 모든 시각 필드(`occurredAt`, `endTime`, `createdAt` 등) 의 ISO 8601 + timezone 표기. 클라 전송 vs DB 저장 vs UI 표시 — UTC vs KST 어느 단계에서 변환.

**#17 summary 집계 단위** — `GET /sessions/{id}/feedback-summary` 응답 구성. `feedback_type` 별 카운트만 vs sync_rate avg/min/max 까지 포함 vs 시간순 추이 추가.

**#18 events 페이징** — `GET /sessions/{id}/feedbacks` 결함 이벤트가 많을 때 (rep 30 × 결함 7 ≈ 210건 가능) 페이징 도입 여부.

**#19 트레이너 권한 헤더** — 본인이 아닌 트레이너가 학생의 `/sessions/{id}/feedbacks` 등 조회 시 인증·인가 처리. JWT role 클레임 활용.

### 🟢 차순위

**#20 batch 응답 형식** — ✅ **2026-05-26 해소**. proto `FeedbackBatchResponse{session_id, saved_count}` 박힘.

**#21 batch 크기 한도** — 🟢 **자연 해소 경향**. gRPC 기본 max message size 4MB. `FeedbackEvent` 약 50 bytes 가정 시 수만 건까지 안전. 베타 진입 후 측정 결과 필요시 split.

**#22 batch 타임아웃** — ✅ **2026-05-26 해소**. AI 측 `httpx` 사라짐 (gRPC 단일화). gRPC deadline ~10초 권장.

**#23 내부 토큰 관리·회전** — 🟡 **부분 해소**. gRPC `Authorization: Bearer` 단일 토큰으로 단순화 (`X-Internal-Token` 사라짐). 1학기는 환경변수 고정으로 충분, 운영 단계 회전 정책은 잔여.

**#24 빈 결과 처리** — `GET /exercises/{id}/feedback-templates` 호출 시 사용자 페르소나에 맞는 row 가 없을 때 — `persona IS NULL` 공통 row fallback (BE-13 권장) vs BEGINNER fallback vs 404.

**#25 enum 응답 메타 (priority)** — templates 응답에 `priority` 컬럼 포함할지. 클라는 priority 안 쓰고 AI 측만 사용 → 응답 제외 가능 (페이로드 절감).

**#26 종료 신호 safety net** — 클라가 종료 신호 안 보내고 앱 강제 종료 시 — AI 가 N분 동안 frame 미수신 시 자동 batch 송신 (분기 ET-C 거부했으나 *안전망* 으로 도입 가능).

**#27 세션 메모리 정리** — AI 측 세션 누적 데이터 (판정 이벤트 list) 를 batch 송신 성공 후 *즉시* 삭제 vs N분 후 vs LRU.

**#28 enum 추가 시 배포 순서** — 새 `FeedbackType` 추가 시 Spring (DB seed + enum) → AI (proto·상수) → Front (캐시 키 인식). 동시 배포 강제 vs 단계적 호환성 (optional 필드).

---

## 당사자별 추출

```
3자 (5건):           1, 2, 5, 16, 28
AI ↔ Spring (8건):    3, 4, 10, 11, 20, 21, 22, 23
AI ↔ Front (2건):     6, 26
Front ↔ Spring (10건): 7, 12, 13, 14, 15, 17, 18, 24, 25, 19(권한)
AI 단독 (3건):        8, 9, 27
Spring 단독 (1건):    19, 23 — *재정렬 후*
```

---

## Spring API 별 영향도 (관여 안건 수 기준)

| Spring API | 안건 # | 관여 수 |
|---|---|:-:|
| gRPC `ReportFeedbackBatch` (← ~~`POST /internal/feedback/batch`~~) | 1 + ~~3, 5, 10, 11, 20, 22~~ 해소 + 16, 21, 23 잔여 | **잔여 4** (10→4) |
| `GET /exercises/{id}/feedback-templates` | 1, 2, 12, 13, 24, 25 | 6 |
| `GET /sessions/{id}/feedbacks` | 1, 16, 18, 19, 28 | 5 |
| `GET /sessions/{id}/feedback-summary` | 1, 16, 17, 19 | 4 |
| `PATCH /sessions/{id}/end` (ET-H) | ~~7~~ 해소 + 16 | 잔여 1 |
| `PATCH /preferences/tts` | 14, 15 | 2 |
| `GET /preferences/tts` | 15 | 1 |
| `PATCH /users/me/persona` | 13 | 1 |
| `GET /users/me` (persona 포함) | 2 | 1 |
| (Spring API 없음) | 6, 8, 9, 26, 27 | — |

→ **batch endpoint 가 가장 합의 부담 큼 (10건)** — Spring 담당자가 먼저 schema·정책 확정 후 AI 담당자에게 전달이 자연스러움.

---

## BE 작업별 협의 매핑

`22-backend-tasks-detail.md` 의 BE-13/14/15 (TTS 관련 신규 작업) 진행 시 *누구와 무엇을 합의해야 하는지* 작업 단위로 정리.

### BE-13 — TTS 피드백 템플릿 페르소나 분기 적용 (🟡, 2.5h)

| 협의 안건 # | 누구와 | 시점 | 결정 옵션 / 권장 |
|:-:|:-:|:-:|---|
| #1 8종 enum 표기 | **3자** | 🔴 작업 전 | `KNEE_OUT` UPPER_SNAKE / master = `REQUIREMENTS.md` §6 (이미 `FeedbackType.java` 와 일치 — *문서 명시만*) |
| #2 페르소나 enum 표기 | **3자** | 🔴 작업 전 | `Member.selectedPersona` 와 `12-persona-difficulty.md` 정합 확인 |
| #12 templates 응답 구조 | Front | 🟡 작업 중 | `[{feedbackType, message}]` Array 권장 |
| #13 페르소나 변경 후 캐시 무효화 | Front | 🟡 작업 중 | PATCH 응답에 `templatesReloadRequired: true` vs 클라 운동 시작 시 항상 재호출 |
| #24 페르소나 row 없을 시 fallback | Front | 🟡 작업 중 | `persona IS NULL` 공통 row fallback (repo `findByExerciseAndPersonaWithFallback` 정합) |
| #25 `priority` 응답 메타 | Front | 🟢 작업 중 | 클라 미사용 → 응답 제외 권장 |
| #28 enum 추가 배포 순서 | 3자 | 🟢 운영 단계 | Spring 먼저 (seed + enum) → AI → Front |

**차단 요소**: #1·#2 (3자 미팅 1회로 해결). 나머지는 작업 중 결정.

### BE-14 — Session 종료 endpoint (🔴, 1.5h)

| 협의 안건 # | 누구와 | 시점 | 결정 옵션 / 권장 |
|:-:|:-:|:-:|---|
| #5 인증·토큰 endpoint 분리 | **3자** | 🔴 작업 전 | `/api/*` JWT / `/internal/*` `X-Internal-Token` |
| #7 클라 양방향 호출 순서 | **Front** | 🔴 작업 전 | `Promise.all` 동시 / 부분 실패 허용 (Spring·AI 독립 책임) |
| #6 (AI 측) 종료 신호 형식 | AI ↔ Front | 🟡 BE-14 와 병행 | *Spring 무관* — 클라가 Spring·AI 양쪽 호출하는 것만 알아두면 됨 |
| #16 시간대 형식 | 3자 | 🟡 작업 중 | ISO 8601 + `+09:00` / DB UTC / 클라 KST. `endTime` 은 *서버 시각 권위* 권장 |

**차단 요소**: #5·#7 (각각 3자 미팅·Front 1:1)

### BE-15 — 세션 피드백 조회 API (🟡, 2.5h)

| 협의 안건 # | 누구와 | 시점 | 결정 옵션 / 권장 |
|:-:|:-:|:-:|---|
| #1 8종 enum 표기 | **3자** | 🔴 작업 전 | BE-13 과 동일 (3자 미팅에서 일괄) |
| #16 시간대 형식 | **3자** | 🔴 작업 전 | BE-14 와 동일 |
| #17 summary 집계 단위 | Front | 🟡 작업 중 | `feedback_type` 별 카운트 + sync_rate avg/min/max |
| #18 events 페이징 | Front | 🟢 차순위 | MVP 단순 list, 운영 시 페이징 |
| #19 트레이너 권한 헤더 | S 단독 | 🟡 작업 중 | 기존 권한 모듈 (JWT role 클레임) 재사용 |

**차단 요소**: #1·#16 (3자 미팅에서 BE-13·14 와 동시 해결)

### ~~기존 InternalFeedbackController — 사후 *공식화* 만~~ → ✅ 2026-05-26 폐기·gRPC 통일

기존 REST `InternalFeedbackController` 가 gRPC `ExerciseGrpcService.reportFeedbackBatch` 로 대체됨. 본 표의 협의 안건들은 대부분 *gRPC 통일 결정* 으로 해소. 잔여만 진행.

| 협의 안건 # | 누구와 | 시점 | 현재 코드 상태 (2026-05-26) |
|:-:|:-:|:-:|---|
| ~~#3 batch payload schema~~ | — | ✅ 해소 | proto `FeedbackBatchRequest` 가 schema 강제 |
| ~~#10 재시도·멱등성~~ | — | ✅ 해소 | `session_feedback_logs` uniqueKey + INSERT IGNORE 박힘 |
| ~~#11 부분 실패~~ | — | ✅ 해소 | gRPC unary — 전체 batch 1 응답 (`INVALID_ARGUMENT` 시 전체 reject + AI 재송신) |
| ~~#20 응답 형식~~ | — | ✅ 해소 | proto `FeedbackBatchResponse{session_id, saved_count}` |
| #21 batch 크기 한도 | A ↔ S | 🟢 운영 후 | gRPC 기본 max 4MB. 자연 여유 |
| ~~#22 타임아웃~~ | — | ✅ 해소 | httpx 사라짐. gRPC deadline ~10초 |
| #23 내부 토큰 관리 | S 단독 | 🟡 잔여 | gRPC `Authorization: Bearer` 단일화로 단순화. 회전 정책만 미정 |

---

## 작업 시작 차단 요소 추출

```
🔴 모든 BE 작업 시작 차단 — 3자 미팅 1회로 일괄 해결:
  #1  8종 enum master  (BE-13, BE-15)
  #2  페르소나 enum master  (BE-13)
  #5  endpoint prefix 분리  (BE-14)
  #16 시간대 형식  (BE-14, BE-15)
  #28 enum 추가 배포 순서  (BE-13)
  → 5건 = 미팅 30분이면 결정

🔴 BE-14 시작 차단 — Front 1:1 (15분):
  #7  클라 양방향 호출 순서  (Spring 응답 spec + Front 호출 형태)

🟡 작업 중 자연 결정 — 차단 아님:
  #12·#13·#17·#24  (Front 1:1, API spec 초안 공유로 결정)
  #19  (Spring 단독, 기존 권한 모듈 재사용)

🟢 사후 명문화 — MVP 차단 아님:
  나머지 (#3, #6, #8~11, #18, #20~23, #25~27)
```

→ **실질 차단은 미팅 30분 + Front 1:1 15분 = 합계 45분** 정도. 나머지는 코드 작업하면서 결정.

### 3자 미팅 1회 안건 (제안)

> 📋 미팅 진행용 1장 문서: [`./3way-meeting-agenda.md`](./3way-meeting-agenda.md) — 각 안건의 현재 상태·결정 옵션·추천안·산출물 위치 + 미팅 후 액션 분배 포함

| 안건 | 결정 사항 (제안) | 합의 후 산출 |
|---|---|---|
| #1 8종 enum master | `REQUIREMENTS.md` §6 master, `FeedbackType.java` 정합 확인 | docs 정합성 확인 |
| #2 페르소나 enum master | `12-persona-difficulty.md` master, `SelectedPersona` 정합 확인 | docs 정합성 확인 |
| #5 endpoint prefix 분리 | `/api/*` JWT / `/internal/*` `X-Internal-Token` | `07-api-design.md` 공통 섹션 |
| #16 시간대 형식 | ISO 8601 + `+09:00` / DB UTC / 클라 KST | `07-api-design.md` 공통 섹션 |
| #28 enum 추가 시 배포 순서 | Spring 먼저 (DB seed + enum) → AI → Front | 운영 가이드 |

---

## 합의 산출물 (체크리스트 완료 후)

| 산출물 | 위치 | 내용 |
|---|---|---|
| 8종 enum 정식 정의 | `docs/REQUIREMENTS.md` §6 master 화 | enum 표기·의미·운동별 활성화 매핑 |
| 페르소나 enum 정식 정의 | `docs/12-persona-difficulty.md` master | enum 표기·라벨·기준 sync_rate |
| batch API 명세 | `docs/07-api-design.md` | request/response schema, 인증, 재시도, 멱등성 |
| 종료 trigger API 명세 | `docs/07-api-design.md` | Spring `PATCH /sessions/{id}/end` + AI 측 신호 형식 |
| proto 동기화 PR | `ai-server/app/proto/exercise.proto` + `backend/src/main/proto/exercise.proto` | `feedback_type` 필드 추가 |
| 시간대·시간 형식 가이드 | `docs/07-api-design.md` 공통 섹션 | ISO 8601 + tz / DB UTC / 클라 KST |
| 토큰 분리 정책 | `docs/handoff/ai-h2-auth-middleware.md` 확장 또는 별도 보안 문서 | `/api/*` vs `/internal/*` |

---

## 진행 권장 순서

1. **(이번 주)** 🔴 최우선 5건 — 3자 미팅 1회로 결정
2. **(작업 시작 전)** 🟡 batch 관련 (3, 10, 11, 20, 21, 22) — Spring 담당자가 schema 초안 → AI 검토
3. **(구현 중)** 🟡 종료 trigger (6, 7, 26) — Front 작업 시점에 합의
4. **(통합 단계)** 🟡 templates·preferences (12, 13, 14, 15, 24, 25) — Front 캐시·UI 작업 시점
5. **(MVP 출시 후)** 🟢 차순위 — 운영하면서 결정

---

## 관련 문서

- [`../decisions/tts-design.md`](../decisions/tts-design.md) — TTS 전체 설계 (분기 1~9, §10·§11·§12)
- [`./ai-tts-feedback-batch.md`](./ai-tts-feedback-batch.md) — AI 측 작업 요청서 (구현 코드 예시)
- [`./ai-h2-auth-middleware.md`](./ai-h2-auth-middleware.md) — 선행 작업 (H2 인증 미들웨어)
- [`../REQUIREMENTS.md`](../REQUIREMENTS.md) §5·6·8 — 요구사항 근거
- [`../12-persona-difficulty.md`](../12-persona-difficulty.md) — 페르소나 정의
- [`../07-api-design.md`](../07-api-design.md) — API 명세 (배치 endpoint 추가 대상)
