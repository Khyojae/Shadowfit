# TTS 피드백 — 구현 전 협의 체크리스트

마지막 업데이트: 2026-05-25
배경: [`../decisions/tts-design.md`](../decisions/tts-design.md) §12 — 8-A 채택 후 구현 시작 전 Front/AI/Spring 3자가 합의해야 할 경계 계약 28건
연관: [`./ai-tts-feedback-batch.md`](./ai-tts-feedback-batch.md) — AI 측 작업 요청서

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

## 🔴 최우선 — 통합 전 반드시 합의

| # | 안건 | 관련 Spring API / 인터페이스 | 결정 옵션 | 당사자 | 상태 |
|:-:|---|---|---|:-:|:-:|
| 1 | 8종 enum 표기 | `POST /internal/feedback/batch`, `GET /sessions/{id}/feedbacks`, `GET /sessions/{id}/feedback-summary`, `GET /exercises/{id}/feedback-templates`, proto `feedback_type` | `KNEE_OUT` UPPER_SNAKE 통일 / master = `REQUIREMENTS.md` §6 | 3자 | ☐ |
| 2 | 페르소나 enum 표기 | `GET /users/me`, `PATCH /users/me/persona`, `GET /exercises/{id}/feedback-templates` | `BEGINNER/ADVANCED/DIET/REHAB` / master = `12-persona-difficulty.md` | 3자 | ☐ |
| 3 | batch payload schema | `POST /internal/feedback/batch` | **snake_case** `{session_id, set_no, is_final, events:[{feedback_type, sync_rate_at_trigger, occurred_at}]}` + Spring DTO 에 `@JsonNaming(SnakeCaseStrategy.class)`. `set_no`·`is_final` 은 BT-SET (분기 2.A.BT) 채택 결과 | A ↔ S | ☐ |
| 4 | proto `feedback_type` 필드 | `ai-server/app/proto/exercise.proto` + `backend/src/main/proto/exercise.proto`, `POST /pose` 응답 모델 | string + 화이트리스트 검증 / 필드 번호 양쪽 동기 | A ↔ S | ☐ |
| 5 | 인증·토큰 endpoint 분리 | 전체 — `/api/*` (JWT) vs `/internal/*` (`X-Internal-Token`) | 경로 prefix 로 명확 분리 | 3자 | ☐ |

---

## 🟡 중요 — 구현 중 결정

| # | 안건 | 관련 Spring API / 인터페이스 | 결정 옵션 | 당사자 | 상태 |
|:-:|---|---|---|:-:|:-:|
| 6 | 세션 종료 신호 형식 (ET-A) | (AI 측) 마지막 `POST /pose` 의 `session_end=true` vs 별도 `POST /sessions/{id}/end` — Spring 의 `PATCH /sessions/{id}/end` 와는 별개 | 옵션 1 (플래그) 또는 옵션 2 (endpoint) 결정 | A ↔ F | ☐ |
| 7 | 클라 양방향 호출 순서 | (Front) `PATCH /sessions/{id}/end` + AI 측 종료 신호 | `Promise.all` 동시 / 부분 실패 허용 | F ↔ S | ☐ |
| 8 | 분류 임계값 위치 | (Spring API 없음 — AI 내부) | `squat_analyzer` 상수 / 영상 5~10건 튜닝 | A 단독 (공유) | ☐ |
| 9 | priority 상수 위치 | (Spring API 없음 또는 `GET /exercises/{id}/feedback-templates` 응답에 포함) | AI 내장 (3-A-1) — 3-A-2 거부 | A 단독 | ☐ |
| 10 | batch 재시도·멱등성 | `POST /internal/feedback/batch` | **BT-SET 채택으로 필수**. AI 측 휴식 중 retry (0s/5s/15s/35s backoff, 총 ~55s) + Spring 측 `(session_id, occurred_at, feedback_type)` uniqueKey + `INSERT IGNORE`/`ON DUPLICATE KEY UPDATE` | A ↔ S | ☐ |
| 11 | batch 부분 실패 처리 | `POST /internal/feedback/batch` | 전체 reject vs 유효한 것만 insert + reject 목록 응답 | A ↔ S | ☐ |
| 12 | templates 응답 구조 | `GET /exercises/{id}/feedback-templates` | `{type: message}` Map vs `[{type, message}]` Array | F ↔ S | ☐ |
| 13 | 페르소나 변경 후 캐시 무효화 | `PATCH /users/me/persona` → `GET /exercises/{id}/feedback-templates` 재호출 | PATCH 응답에 `templatesReloadRequired: true` vs 클라가 운동 시작 시 항상 재호출 | F ↔ S | ☐ |
| 14 | `ttsSpeed` 검증 | `PATCH /preferences/tts` | 0.5~2.0 범위 위배 시 422 vs 자동 클램프 | F ↔ S | ☐ |
| 15 | TTS preferences 즉시 효과 | `GET /preferences/tts`, `PATCH /preferences/tts` | 현재 운동 중 변경은 다음 rep 부터 / 클라 cached value 사용 | F ↔ S | ☐ |

---

## 🟢 차순위 — 구현 후 보완

| # | 안건 | 관련 Spring API / 인터페이스 | 결정 옵션 | 당사자 | 상태 |
|:-:|---|---|---|:-:|:-:|
| 16 | 시간대 형식 | 시각 필드 포함 모든 API — `POST /internal/feedback/batch`, `GET /sessions/{id}/feedbacks`, `PATCH /sessions/{id}/end`, `GET /sessions/{id}/feedback-summary` | ISO 8601 + `+09:00` / DB UTC 저장 / 클라 KST 표시 | 3자 | ☐ |
| 17 | summary 집계 단위 | `GET /sessions/{id}/feedback-summary` | `feedback_type` 별 카운트 + sync_rate avg/min/max | F ↔ S | ☐ |
| 18 | events 페이징 | `GET /sessions/{id}/feedbacks` | `page/size` (max ~210건 가능) | F ↔ S | ☐ |
| 19 | 트레이너 권한 헤더 | `GET /sessions/{id}/feedbacks`, `GET /sessions/{id}/feedback-summary` | 기존 권한 모듈 재사용 | S 단독 | ☐ |
| 20 | batch 응답 형식 | `POST /internal/feedback/batch` | `200 OK {insertedCount}` vs `204 No Content` | A ↔ S | ☐ |
| 21 | batch 크기 한도 | `POST /internal/feedback/batch` | events 최대 200~500, 초과 시 split | A ↔ S | ☐ |
| 22 | batch 타임아웃 | `POST /internal/feedback/batch` (AI 측 httpx timeout) | Spring 응답 대기 10초 | A ↔ S | ☐ |
| 23 | 내부 토큰 관리·회전 | `POST /internal/feedback/batch` 인증 | 환경변수 → secret manager 단계적 / 회전 정책 미정 | S 단독 | ☐ |
| 24 | 빈 결과 처리 | `GET /exercises/{id}/feedback-templates` | 페르소나 row 없으면 BEGINNER fallback vs 404 | F ↔ S | ☐ |
| 25 | enum 응답 메타 (priority) | `GET /exercises/{id}/feedback-templates` | `priority` 응답 제외 (클라 미사용) | F ↔ S | ☐ |
| 26 | 종료 신호 safety net | (AI 측 종료 처리, Spring API 무관) | 클라 신호 누락 시 N분 timeout 으로 batch 송신 (ET-C 미니멀 도입) | A ↔ F | ☐ |
| 27 | 세션 메모리 정리 | (Spring API 없음) | batch 송신 성공 후 즉시 누적 데이터 삭제 | A 단독 | ☐ |
| 28 | enum 추가 시 배포 순서 | 모든 enum 사용 API | Spring 먼저 (DB seed + enum) → AI → Front | 3자 | ☐ |

---

## 각 안건 상세 설명

위 표의 "안건" 컬럼이 짧아 *무엇이 문제인지* 빠르게 안 보일 수 있어 간략 설명.

### 🔴 최우선

**#1 8종 enum 표기** — 자세 결함 분류 enum (`KNEE_OUT`, `KNEE_IN`, `HIP_HIGH`, `BACK_BENT` 등 8종) 의 *표기·정의 master 위치*. proto / Spring `FeedbackType.java` / Python 상수 / Front 문자열 / DB seed **5곳** 이 일치해야 하므로 master 가 어디인지 명시 안 하면 drift 발생.

**#2 페르소나 enum 표기** — 사용자 페르소나 (`BEGINNER`/`ADVANCED`/`DIET`/`REHAB`) 의 *master 위치*. `Member.selectedPersona` 와 `12-persona-difficulty.md` 가 정합한지 확인 + 문서·코드 어느 쪽이 master 인지 명시.

**#3 batch payload schema** — AI 가 *세트 경계마다* Spring 에 보내는 `POST /internal/feedback/batch` 의 request body (분기 2.A.BT BT-SET 채택). **snake_case 채택** — Pydantic 기본·proto 공식 컨벤션 + AI 측 변환 코드 불필요 ([[feedback-minimize-python-changes]] 정합). Spring DTO 2개 (`FeedbackBatchRequestDto`, `FeedbackEventDto`) 에 `@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)` 어노테이션 + `set_no`·`is_final` 필드 추가. Java 필드명은 camelCase 그대로.

**#4 proto `feedback_type` 필드** — AI ↔ Spring gRPC 의 `RepCompletedEvent` + Pose API 응답에 추가할 신규 필드. proto 파일 양쪽(`ai-server/app/proto/exercise.proto`, `backend/src/main/proto/exercise.proto`) 동기 + 필드 번호·타입 결정.

**#5 인증·토큰 endpoint 분리** — 사용자 JWT 와 내부 토큰을 endpoint 경로로 분리. `/api/*` → JWT 인증, `/internal/*` → `X-Internal-Token`. 정책 충돌·외부 노출 방지.

### 🟡 중요

**#6 (AI 측) 세션 종료 신호 형식** — AI 가 "세션 끝났음" 을 인지하는 방법. 마지막 `POST /pose` 의 `session_end=true` 플래그 vs 별도 `POST /sessions/{id}/end` endpoint. Spring 의 `PATCH /sessions/{id}/end` 와는 *별개 채널*.

**#7 클라 양방향 호출 순서** — 종료 시 클라가 Spring(`PATCH /sessions/{id}/end`) 과 AI(종료 신호) 양쪽 호출. 동시 (`Promise.all`) vs 직렬, 부분 실패 시 처리 정책.

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

**#20 batch 응답 형식** — `POST /internal/feedback/batch` 응답 — `200 OK {insertedCount: 12}` JSON vs `204 No Content`. AI 측 검증 정보 필요한가.

**#21 batch 크기 한도** — events 최대 개수 제한 (200·500?). 한 세션 결함 이벤트가 그 이상 누적 시 AI 가 split 송신 vs Spring 이 자체 처리.

**#22 batch 타임아웃** — AI 측 `httpx` 클라이언트의 Spring 응답 대기 시간 (5초·10초). 초과 시 retry 정책과 연결.

**#23 내부 토큰 관리·회전** — `X-Internal-Token` 값의 저장 위치 (환경변수 → secret manager 단계적 이전) 및 정기 회전 정책. 1학기는 환경변수 고정으로 충분, 운영 단계 강화.

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
| `POST /internal/feedback/batch` | 1, 3, 5, 10, 11, 16, 20, 21, 22, 23 | **10** |
| `GET /exercises/{id}/feedback-templates` | 1, 2, 12, 13, 24, 25 | 6 |
| `GET /sessions/{id}/feedbacks` | 1, 16, 18, 19, 28 | 5 |
| `GET /sessions/{id}/feedback-summary` | 1, 16, 17, 19 | 4 |
| `PATCH /sessions/{id}/end` (ET-A) | 7, 16 | 2 |
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

### 기존 InternalFeedbackController — 사후 *공식화* 만

코드는 구현되어 있으나 운영·확장 시 합의 명문화 필요. *BE 신규 작업 차단 요소 아님*.

| 협의 안건 # | 누구와 | 시점 | 현재 코드 상태 |
|:-:|:-:|:-:|---|
| #3 batch payload schema | A ↔ S | 🟢 사후 명문화 | `FeedbackBatchRequestDto` 이미 구현 |
| #10 재시도·멱등성 | A ↔ S | 🟡 BE-13 시점 | `session_feedback_logs` uniqueKey 없음 — 재송신 시 중복 가능. **BE-13 의 schema 변경 시 같이 검토 권장** |
| #11 부분 실패 | A ↔ S | 🟢 사후 | `saveBatch` 트랜잭션 정책 확인 |
| #20 응답 형식 | A ↔ S | 🟢 사후 | 현재 문자열 응답, JSON 권장 |
| #21 batch 크기 한도 | A ↔ S | 🟢 운영 후 | 현재 무제한 |
| #22 타임아웃 | A 측 | 🟢 운영 후 | AI 측 httpx |
| #23 내부 토큰 관리 | S 단독 | 🟢 사후 | `application.yml` 환경변수, 회전 정책 미정 |

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
