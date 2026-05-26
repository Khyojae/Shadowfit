# ShadowFit 요구사항 정리

현재까지 구현된 코드 기준으로 도메인별 요구사항을 정리한 문서.

---

## 1. 시스템 구성

| 구성 요소 | 역할 | 기술 |
|---|---|---|
| Spring Boot 백엔드 | 비즈니스 로직, DB, 인증, 외부 API 게이트웨이 | Java, Spring Security, JPA |
| FastAPI AI 서버 | 포즈 추정, 싱크로율 계산, 영상 분석 | Python, MediaPipe |
| Spring ↔ FastAPI 통신 | 운동 분석 시작 / 포즈 수신 | gRPC + 내부 REST(`X-Internal-Token`) |

---

## 2. 회원 / 인증 (`/member`)

| 기능 | 메서드 / 경로 | 비고 |
|---|---|---|
| 회원가입 | `POST /member/signup` | `MemberRequestDto` |
| 로그인 | `POST /member/login` | JWT 발급 (`LoginResponseDto`) |
| 로그아웃 | `POST /member/logout` | `JwtBlacklist`로 토큰 무효화 |
| 회원탈퇴 | `DELETE /member/{email}` | |
| 회원정보(온보딩) 조회 | `GET /member/onboarding/{email}` | |
| 온보딩 단계별 저장 | `PATCH /member/onboarding/{email}` | 키/몸무게/레벨/페르소나/선호영상 부분 수정 |

추가 구성요소: `RefreshToken` 엔티티, `JwtAuthFilter`, `CustomAuthenticationEntryPoint`, `CustomAccessDeniedHandler`

---

## 3. 온보딩 / 사용자 속성 (`Member` 엔티티)

| 항목 | 값 |
|---|---|
| 페르소나 | `SelectedPersona` (기본 BEGINNER) |
| 운동 레벨 | `WorkoutLevel` |
| 신체 정보 | height, weight (DECIMAL 5,1) |
| 선호 영상 | `preferredUrl` (YouTube, `YoutubeValidator`로 검증) |
| 온보딩 완료 플래그 | `onboardingCompleted` |
| TTS 설정 | `ttsEnabled`(기본 true), `ttsSpeed`(기본 1.0, 0.5~2.0) |

---

## 4. 운동 분석 세션 (`/exercises`)

| 기능 | 메서드 / 경로 | 흐름 / 비고 |
|---|---|---|
| 기준 좌표 추출 | `POST /exercises/{exerciseId}/reference?youtubeUrl=` | YouTube → MediaPipe → `ExerciseReference` 저장 |
| 운동 세션 시작 | `POST /exercises/sessions` | App → Spring → gRPC → FastAPI, 즉시 sessionId 반환 |
| 운동 세션 종료 | `PUT /exercises/sessions/{sessionId}/complete` | totalReps 등 받아서 COMPLETED 처리 |
| 세션 타임아웃 자동 실패 | 스케줄러 (1분 주기) | 시작시간 + 예상시간 + 30분 버퍼 초과 시 FAILED, `@Version` 낙관락으로 FastAPI 결과 우선 |

세션 상태(`Status` enum): `IN_PROGRESS`, `COMPLETED`, `FAILED`

---

## 5. 포즈 데이터 / 내부 API (`/internal/*`)

| 기능 | 메서드 / 경로 | 비고 |
|---|---|---|
| 포즈 데이터 배치 저장 | `POST /internal/exercises/pose-data` | FastAPI가 sessionId별 배치 전송, `X-Internal-Token` 검증 |
| 피드백 발화 이벤트 배치 저장 | **gRPC `ExerciseService.ReportFeedbackBatch`** (FastAPI → Spring) | 세션 종료 / 세트 경계 (BT-SET, 분기 2.A.BT) 시 FastAPI 가 batch 송신. 실시간 매 rep 호출 금지. ~~REST `POST /internal/feedback/batch` 폐기 (2026-05-26 gRPC 통일 결정)~~ |

---

## 6. 피드백 템플릿 (`/exercises/{exerciseId}/feedback-templates`)

| 항목 | 내용 |
|---|---|
| 조회 API | `GET` — 세션 시작 시 클라이언트가 받아서 device TTS로 매핑 |
| `FeedbackType` 8종 | KNEE_OUT/IN, HIP_LOW/HIGH, BACK_BENT, SHOULDER_TILT, ELBOW_BENT, HEAD_DOWN |
| 템플릿 속성 | (exercise + feedback_type) 유니크, `priority`로 다중 검출 시 우선순위 |
| 발화 로그 | `SessionFeedbackLog` (sync_rate_at_trigger, occurred_at) |

---

## 7. 운동 기록 / 리포트 (`/reports`)

| 기능 | 메서드 / 경로 | 응답 |
|---|---|---|
| 주간 운동 요약 | `GET /reports/weekly-summary` | `WeeklyActivityResponseDto` |
| 메인 달력 데이터 | `GET /reports/calendar?year&month` | `CalendarMainResponseDto` |
| 일일 메모 작성/수정 | `POST /reports/daily-logs` | `DailyLog` upsert |
| 세션 상세 보고서 | `GET /reports/session/{sessionId}` | `SessionReportResponseDto` (worst 구간, 이전 비교, 파트별 점수) |

`DailyLog`: 메모, 기분(`Mood`: GREAT~TERRIBLE), 누적 운동시간, 누적 칼로리

---

## 8. TTS 환경설정 (`/preferences`)

| 기능 | 메서드 / 경로 |
|---|---|
| TTS 설정 조회 | `GET /preferences/tts` |
| TTS 설정 변경 | `PATCH /preferences/tts` (활성화 여부, 속도 0.5~2.0) |

---

## 9. 관리자 (`/admin/exercises`)

| 기능 | 메서드 / 경로 | 권한 |
|---|---|---|
| 싱크로율 임계값 변경 | `PATCH /admin/exercises/{exerciseId}/thresholds` | `ROLE_ADMIN`, `beginner < advanced` 필수 |

---

## 10. AI 서버 엔드포인트 (`/api/v1`)

| 경로 | 기능 |
|---|---|
| `POST /pose` | 단일 이미지 포즈 추정 |
| `POST /sync` | 싱크로율 계산 |
| `GET /sync/onboarding-guide` | 온보딩 가이드 데이터 |
| `POST /video/analyze` | 영상 업로드 분석 (기준 좌표 추출용) |

---

## 핵심 아키텍처 결정사항

- **언어 정책**: 사용자 노출 텍스트(피드백 멘트, 메모 등)는 한국어 단일. 다국어 분리 필드를 만들지 않음.
- **동시성**: `Session`에 `@Version` — FastAPI 완료 콜백 vs 타임아웃 스케줄러 충돌 시 FastAPI 우선.
- **실시간 부하 분리**: 운동 중 발화 피드백은 device TTS로 처리하고, 서버 저장은 종료 시 배치 1회.
- **내부 API 보호**: FastAPI ↔ Spring 호출은 `X-Internal-Token` 헤더 + gRPC `InternalAuthInterceptor`.





