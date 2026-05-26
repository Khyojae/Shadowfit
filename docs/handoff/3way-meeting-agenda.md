# 3자 미팅 안건 — TTS 피드백 통합 전 합의

마지막 업데이트: 2026-05-25
참석: Spring 담당자 / FastAPI(AI) 담당자 / Front (React Native) 담당자
소요: **약 30분** (5 안건 × 5분)
배경: [`./tts-negotiation-checklist.md`](./tts-negotiation-checklist.md) #1·#2·#5·#16·#28 — 모든 BE 작업(BE-13/14/15) + AI handoff(`ai-tts-feedback-batch.md`) + Front 통합의 *작업 시작 차단 요소*

---

## 목적

TTS 피드백 기능을 3 포지션 병행 작업 가능하게 하기 위한 *경계 계약* 5건 일괄 결정. 미팅 후 모든 측의 코드 작업이 차단 없이 시작 가능.

각 안건은 다음 형식:
- **현재 상태** — 코드·문서가 어디까지 와 있는가
- **결정 옵션** — 후보안
- **추천** — 권장 선택 + 사유
- **결정 후 산출물** — 어디에 박제하는가
- **결정 사항** ← *미팅 중 기록*

---

## 안건 1 — 8종 `FeedbackType` enum master 확정

### 현재 상태
- `backend/src/main/java/com/shadowfit/model/exercise/FeedbackType.java` — 8종 enum 구현됨 (`KNEE_OUT`, `KNEE_IN`, `HIP_LOW`, `HIP_HIGH`, `BACK_BENT`, `SHOULDER_TILT`, `ELBOW_BENT`, `HEAD_DOWN`)
- `docs/REQUIREMENTS.md` §6 — 요구사항으로 8종 명시
- `mysql/data.sql:130-147` — 운동별 seed 데이터에 enum 값 사용 중 (스쿼트 4건: `KNEE_OUT`/`KNEE_IN`/`HIP_HIGH`/`BACK_BENT`)
- AI 측 — *아직 분류 함수 미구현*. 구현 시 `KNEE_OUT` 형식 문자열 송신 예정
- Front — `feedback_type` 문자열 받아서 templateCache lookup 예정

### 결정 옵션

| 옵션 | 의미 |
|---|---|
| **A. `REQUIREMENTS.md` §6 master** | 요구사항 문서가 권위. 코드 변경 시 docs 먼저 갱신 |
| B. `FeedbackType.java` master | 코드가 권위. 변경 시 코드 → docs 동기화 |
| C. proto master | 양쪽 proto 가 권위. enum 자체를 proto 의 `enum FeedbackType` 으로 정의 |

### 추천: **A**
- 요구사항이 비즈니스 진실의 출처. 코드는 *구현*
- Spring/AI/Front 가 *docs 를 봐서 동기화* 가능
- proto enum (옵션 C) 은 *string 으로 송수신* 결정과 어긋남 (#4 항목과 함께 결정)

### 결정 후 산출물
- `docs/REQUIREMENTS.md` §6 에 *"8종 enum master 는 본 섹션"* 명시 추가
- `FeedbackType.java`, AI 측 상수, Front 문자열 master 화 — 모두 §6 정합 확인 PR

### 결정 사항 (미팅 기록)
> ☐ 옵션 ___ 채택 / 결정자: ___ / 일자: ___

---

## 안건 2 — `SelectedPersona` enum master 확정

### 현재 상태
- `Member.selectedPersona` (default `BEGINNER`) — `backend/.../model/member/Member.java:36`
- enum 4종 (`BEGINNER`, `ADVANCED`, `DIET`, `REHAB`)
- `docs/12-persona-difficulty.md` — 페르소나별 톤·기준 sync_rate 정의
- `mysql/schema.sql:20` — DB 컬럼 정의
- Front 표시 라벨 ("헬린이"/"헬창"/"다이어트"/"재활") — `12-persona-difficulty.md` 에 정의

### 결정 옵션

| 옵션 | 의미 |
|---|---|
| **A. `12-persona-difficulty.md` master** | 문서 권위 |
| B. `SelectedPersona` enum master | 코드 권위 |

### 추천: **A**
- 페르소나 = 비즈니스 컨셉 (사용자 분류)
- 표시 라벨·톤·기준 sync_rate 모두 *비즈니스 결정*. 코드는 이를 따름

### 결정 후 산출물
- `docs/12-persona-difficulty.md` 최상단에 "본 문서가 페르소나 enum master" 명시
- BE-13 의 seed 데이터 작성 시 본 문서 톤 가이드 참고

### 결정 사항
> ☐ 옵션 ___ 채택 / 결정자: ___ / 일자: ___

---

## 안건 3 — endpoint prefix 인증·토큰 분리 — **✅ 해소됨 (2026-05-26 gRPC 통일)**

> **2026-05-26 해소**: 본 안건 자체가 *AI ↔ Spring REST internal endpoint 가 공존하던 상태* 를 전제로 했으나, gRPC 통일 결정으로 internal REST endpoint 가 모두 사라짐 (`InternalFeedbackController` 삭제). 남은 REST 는 *클라 ↔ Spring* 만이며 전부 JWT (Spring Security). `application.yml` 의 `/internal/**` whitelist 도 제거됨. AI ↔ Spring 인증은 gRPC `Authorization: Bearer` (`InternalAuthInterceptor`) 로 단일화. **별도 prefix 정책 결정 불필요.**

### 결정 사항
> ☑ 무효화 — 2026-05-26 gRPC 통일 결정으로 분기 자체 소멸. 참고: [`../decisions/tts-design.md`](../decisions/tts-design.md) 상단 박스.

---

## 안건 4 — 시간대·시간 형식 — **✅ 사전 해결 (2026-05-25)**

> 미팅 안건에서 제외 가능. 한국 전용 서비스라 *단순화* 로 결정.

### 결정 사항

| 영역 | 채택 |
|---|---|
| **서버 timezone** | Asia/Seoul 고정 (Spring `spring.jackson.time-zone: Asia/Seoul` + AI `TZ=Asia/Seoul`) |
| **API JSON 형식** | `"2026-05-25T10:23:45"` — timezone 마커 없음 |
| **DB 저장** | `LocalDateTime` (현재 상태 유지, KST 가정) |
| **UI 표시** | KST 시간만 |

### 사유
- 한국 전용 서비스 ([[project-korean-only]]) — timezone 마커 가치 약함
- 산업 mainstream — 카카오·네이버·토스 등 한국 단일 서비스는 마커 안 씀
- 작업량 최소 — `application.yml` + `docker-compose.yml` 각 1줄로 끝
- DB 마이그레이션 0 — 기존 `LocalDateTime` 그대로
- 글로벌 진출 시 재검토 (현재 가능성 0)

### 작업 영향
- `application.yml` 에 `spring.jackson.time-zone: Asia/Seoul` 1줄
- `docker-compose.yml` 에 `TZ=Asia/Seoul` env 1줄
- 끝. 코드 변경 없음.

---

## 안건 5 — enum 추가 시 배포 순서

### 현재 상태
- 8종 enum 은 고정 (요구사항 §6) 이나, *스쿼트와 무관한 4종* (`HIP_LOW`·`SHOULDER_TILT`·`ELBOW_BENT`·`HEAD_DOWN`) 은 현재 분류·seed 모두 비활성
- 런지·플랭크 도입 시 활성화 예정
- proto / Spring enum / AI 상수 / Front 문자열 / DB seed — **5곳** 동기화 필요

### 결정 옵션

| 옵션 | 흐름 |
|---|---|
| **A. Spring 먼저 → AI → Front (단계적)** | DB seed + Spring enum 배포 → AI 분류 함수 + proto 배포 → Front 캐시 키 인식. 호환성 보장 (Front 모르는 enum 받아도 무시) |
| B. 3자 동시 배포 강제 | PR 동시 머지. 안전하나 일정 동기화 부담 |
| C. proto optional 필드 | enum 을 string 으로 송수신 (이미 추천) → 새 값도 동적으로 처리. Front 가 모르는 enum 받으면 기본 메시지 |

### 추천: **A + C 결합**
- 운영 단계엔 Spring 먼저 (DB seed) → AI → Front 단계적 배포
- proto 는 이미 string 송수신 결정 (#4 항목과 연계) → Front 가 모르는 enum 받으면 *templateCache miss* 로 자연 처리 (안전)
- 단계적 배포 중 *역호환성* 자동 (Front 가 일부만 알아도 동작)

### 결정 후 산출물
- 운영 가이드에 "enum 추가 절차" 단계 명문화 (가능하면 `docs/operations/deployment-order.md` 또는 README 보강)
- BE-13 의 schema PR 가 *enum 추가 절차의 첫 사례* — 운영 가이드 작성의 trigger

### 결정 사항
> ☐ 옵션 ___ 채택 / 결정자: ___ / 일자: ___

---

## 미팅 후 액션 (담당 분배)

| 산출 | 담당 | 마감 |
|---|---|---|
| `docs/REQUIREMENTS.md` §6 master 명시 PR | Spring | 미팅 +1일 |
| `docs/12-persona-difficulty.md` master 명시 PR | Spring | 미팅 +1일 |
| `docs/07-api-design.md` 공통 섹션 (prefix·시간대) 신설 PR | Spring | 미팅 +2일 |
| 운영 가이드 (배포 순서) 초안 | Spring | 미팅 +3일 (BE-13 schema PR 와 함께) |
| 정합성 확인 — 코드 vs docs | Spring + AI | 미팅 +3일 |

---

## 미팅 후 즉시 가능한 작업

| 작업 | 담당 |
|---|---|
| **BE-13** — 페르소나 분기 적용 (schema + Controller) | Spring |
| **BE-14** — Session 종료 endpoint | Spring |
| **BE-15** — 세션 피드백 조회 API | Spring |
| AI handoff 의 6 항목 (proto · 분류 함수 · batch POST · 종료 신호 수신) | AI |
| Front — 템플릿 캐시 + 발화 핸들러 + audio session 설정 | Front |

→ 미팅 후 **3 포지션 병행 작업 가능**.

---

## 본 미팅 *밖* 의 협의 (BE 작업 중 1:1 결정)

미팅에서 다루지 않고 작업 중 자연 결정:

| 안건 # | 누구와 | 안건 | 상태 |
|:-:|:-:|---|:-:|
| #3 | AI | batch payload schema — **snake_case 합의됨** (Spring DTO 에 `@JsonNaming` 추가, BE-13 시점에 처리) | ✅ |
| #7 | Front | 클라 양방향 호출 순서 (BE-14 시작 전 짧은 합의) | ☐ |
| #12 | Front | templates 응답 schema (BE-13 작업 중) | ☐ |
| #13 | Front | 페르소나 변경 후 캐시 무효화 (BE-13 작업 중) | ☐ |
| #17 | Front | summary 집계 단위 (BE-15 작업 중) | ☐ |
| #10 | AI | batch 멱등성·재시도 (BE-13 schema 변경 시) | ☐ |
| #6 | AI ↔ Front | AI 측 종료 신호 형식 (Spring 무관) | ☐ |

각 1:1 협의는 Slack 1회 또는 짧은 미팅으로 충분.

---

## 관련 문서

- [`./tts-negotiation-checklist.md`](./tts-negotiation-checklist.md) — 28건 전체 협의 안건 (본 미팅은 그 중 5건만 다룸)
- [`./ai-tts-feedback-batch.md`](./ai-tts-feedback-batch.md) — AI 측 작업 요청서
- [`../decisions/tts-design.md`](../decisions/tts-design.md) — 분기 1~9 + §10·§11·§12 전체 설계
- [`../tasks/22-backend-tasks-detail.md`](../tasks/22-backend-tasks-detail.md) — BE-13·14·15 작업 상세
- [`../REQUIREMENTS.md`](../REQUIREMENTS.md) §6 — 8종 enum 요구사항
- [`../12-persona-difficulty.md`](../12-persona-difficulty.md) — 페르소나 정의
- [`../07-api-design.md`](../07-api-design.md) — API 공통 정책 (배포 시 prefix·시간대 섹션 추가 대상)