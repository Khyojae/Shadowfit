# 기능 로드맵 — 요구사항 ↔ 코드 매핑

마지막 업데이트: 2026-05-23
근거: 발표 자료 *요구사항 정의* (HO-PT 6쪽) + 현재 코드 상태
목적: PPT 의 검정/회색 마킹과 실제 구현 상태가 어디서 일치·불일치하는지 한눈에 보기, 그리고 **스택별로 무엇이 남았는지** 정리.

읽는 법:
- ✅ = 코드 동작·결합까지 검증됨
- 🟨 = 일부 구현 (보통 한쪽 스택만)
- 🟥 = 미구현

---

## 1. 요구사항 ↔ 코드 매핑

### 1-1. 회원 / 인증 · 온보딩 · 마이페이지 (PPT 검정 굵게 = 구현됨)

| PPT 기능 | Backend | Frontend | 종합 |
|---------|---------|---------|------|
| 회원가입 | `POST /member/signup` | `services/authService.ts:16` | ✅ |
| 로그인 | `POST /member/login` (JWT 발급) | `services/authService.ts:12` | ✅ |
| 온보딩 설정 | `PATCH /member/onboarding/{email}` | `(onboarding)/index.tsx` | ✅ |
| 온보딩 조회 | `GET /member/onboarding/{email}` | 마이페이지 화면 | ✅ |
| 온보딩 수정 | 위와 동일 (PATCH) | 동일 | ✅ |

→ **이 그룹은 PPT 표시대로 완성 상태.**

### 1-2. 실시간 운동 및 AI 운동 분석 (혼합)

| PPT 기능 | PPT 표시 | Backend | AI | Frontend | 남은 작업 |
|---------|---------|---------|-----|---------|---------|
| 운동 시작 기능 | 검정 | `POST /exercises/sessions` ✅ | `StartAnalysis` 수신 ✅ | 🟥 호출 없음 (`exercise.tsx`) | 프론트 — `services/exerciseService.ts` 신설 + 녹화 버튼 핸들러 |
| 운동 중단 기능 | 검정 | `PUT /exercises/sessions/{id}/stop` ✅ | `StopAnalysis` 수신 ✅ | 🟥 호출 없음 | 프론트 — 종료 버튼 핸들러 |
| 운동 횟수 자동 측정 | 검정 | gRPC 수신 OK | `StreamingSquatAnalyzer.rep_count` ✅ | 🟥 카메라 프레임 송신 X | 프론트 — `POST /pose` 송신 (분기 H 결정 후) |
| 운동 구간별 자세 분석 | 검정 | `pose_data.sync_rate/feedback_message` ✅ | rep 단위 sync_rate 산출 ✅ | 🟥 표시 X | 프론트 — 결과 표시 화면 |
| 실시간 운동 데이터 전송 | 회색 | `SavePoseDataBatch` 수신 + DB 저장 ✅ | rep 완성 시 자동 push ✅ (`pose.py:116`) | 🟥 프레임 트리거 X | 프론트 — 위와 같음 |
| AI 분석 서버 연동 | 회색 | gRPC + 인증 ✅ | gRPC 서버·콜백 ✅ | 🟥 진입점 X | 프론트가 운동 API 부르기 시작하면 연결됨 |
| 개인화 TTS 피드백 | 회색 | `/preferences/tts` GET/PATCH ✅, `feedback-templates` ✅ | rep 단위 `feedback_message` 생성 ✅ | 🟥 device TTS 재생 X | 프론트 — `expo-speech` + 멘트 매핑 |
| 관절 색깔 시각화 | 회색 | — | landmarks 좌표 반환 ✅ | 🟨 sync_rate 색상만 (`exercise.tsx:32`) | 프론트 — 관절 점 오버레이 |
| 운동 자세 유사도 분석 | 회색 | — | DTW (`dtw_calculator.py`) ✅ | 🟥 결과 표시 X | 프론트 — sync_rate 표시 + 차트 |
| 운동 세트 자동 구분 | 회색 | 🟥 스키마에 set 개념 없음 | 🟥 분석기 측 set 미구현 | 🟥 | **새 스키마 컬럼 + 분석 로직 + UI** (큰 작업) |
| 운동 타이머 기능 | 회색 | start_time/end_time ✅ | — | 🟥 타이머 UI X | 프론트 — 진행 중 타이머 |
| 운동 데이터 저장 | 회색 | `pose_data` 테이블 ✅ | push 자동 ✅ | 🟥 트리거 X | 프론트 — 프레임 송신 |
| 운동 완료 결과 전송 | 회색 | `CompleteAnalysis` 수신 → 세션 갱신 ✅ | `_send_complete_analysis` ✅ | 🟥 결과 화면 X | 프론트 — 종료 후 결과 보기 |
| 사용자 운동 패턴 분석 | 회색 | 🟥 | — | 🟥 | **백엔드 분석 로직 신설 + UI** |

### 1-3. 운동 리포트 (혼합)

| PPT 기능 | PPT 표시 | Backend | Frontend | 남은 작업 |
|---------|---------|---------|---------|---------|
| 메인 캘린더 대시보드 | 검정 | `SessionService.getCalendarMain` ✅ | 🟨 화면 골격 있을 수도 (확인 필요) | 프론트 — 데이터 연동 |
| 주간 운동 요약 통계 | 검정 | `SessionService.getWeeklyActivity` ✅ | 🟨 동일 | 프론트 — 데이터 연동 |
| 실시간 자세 피드백 | 회색 | feedback_message 컬럼 + AI rep 단위 ✅ | 🟥 표시 X | 프론트 — 실시간 자막/TTS |
| 이전 운동 기록 비교 | 회색 | `Report.comparison_with_previous` JSON ✅ | 🟥 비교 화면 X | 프론트 — 차트·표 |
| 운동 worst 구간 선정 | 회색 | `WorstSectionDto`, `SessionReportResponseDto` 있음, 채우는 서비스 로직은 확인 필요 | 🟥 표시 X | Backend 로직 보강 + 프론트 차트 |
| AI 리포트 자동 생성 | 회색 | 🟥 (`OpenAI_API_KEY` env 만 있음, GPT 호출 코드 미작성) | 🟥 | Backend — `GptFeedbackService` 신설 (Anthropic Claude API 또는 OpenAI) |
| 데이터 기반 개인화 루틴 추천 | 회색 | 🟥 | 🟥 | **큰 작업** — 데이터 분석 + 추천 알고리즘 |
| 운동 목표 달성현황 | 회색 | 🟥 (목표 엔티티 없음) | 🟥 | 새 스키마 + UI |

### 1-4. 관리자 페이지 (대부분 미구현)

| PPT 기능 | Backend | Frontend | 남은 작업 |
|---------|---------|---------|---------|
| 관리자 대시보드 | 🟨 `AdminExerciseController` 일부 (`PATCH /admin/exercises/{id}/thresholds`) | 🟥 화면 없음 | 통계 API + 관리자 화면 |
| 카테고리 관리 | 🟨 `ExerciseCategory` enum 존재, 관리 API 미존재 | 🟥 | CRUD API + 화면 |
| 운동 영상 관리 | 🟨 `POST /exercises/{id}/reference?youtubeUrl=` (등록만) | 🟥 운영자 화면 X | 화면·삭제·재등록 등 |

---

## 2. 스택별 남은 작업

### 2-1. Frontend (React Native) — 🔴 가장 큰 갭

**핵심 사실**: `frontend/services/authService.ts` 는 4개 회원 API 만 호출. 운동·리포트·관리자 API 는 0개. `exercise.tsx:190` 에 "AI 서버 연동 전 테스트용" 주석 명시.

| 우선 | 항목 | 비고 |
|------|------|------|
| 🔴 | `services/exerciseService.ts` 신설 (운동 시작·종료·세션 조회) | 분기 H 무관, 즉시 가능 |
| 🔴 | 카메라 프레임 송신 (`POST /pose` 또는 백엔드 프록시) | 분기 H 결정 후 — [`decisions/ai-backend-coupling.md`](../decisions/ai-backend-coupling.md) §5-β |
| 🔴 | 운동 결과 화면 (rep 수·sync_rate·feedback) | 종료 후 화면 |
| 🟡 | TTS 재생 (`expo-speech`) | `/preferences/tts` + `/feedback-templates` 기반 |
| 🟡 | 관절 점 오버레이 시각화 | AI landmarks 응답 활용 |
| 🟡 | 캘린더·주간 통계 화면 데이터 연동 | API 이미 있음 |
| 🟢 | 리포트 화면 (worst 구간·이전 기록 비교·자세 분석) | Backend 일부 완성 후 |
| 🟢 | 관리자 화면 (대시보드·카테고리·영상) | Backend API 완성 후 |

### 2-2. Backend (Spring) — 🟡 일부 신규 필요

**기존**: 결합·세션·동시성·멱등성·인증·DB 모두 완성. PR #11 로 자동 테스트까지 검증됨.

| 우선 | 항목 | 비고 |
|------|------|------|
| 🟡 | 백엔드 프록시 `POST /exercises/sessions/{id}/frame` | 분기 H1 채택 시 — WebClient 로 AI `/pose` 전달 |
| 🟡 | GPT/Claude 연동 (`GptFeedbackService`) | AI 리포트 자동 생성. `OPENAI_API_KEY` env 이미 준비됨 |
| 🟡 | worst 구간 선정 로직 보강 | DTO 있음, 채우는 서비스 메서드 확인 필요 |
| 🟢 | 운동 목표 엔티티·API (`GoalController`) | 새 도메인 |
| 🟢 | 카테고리 관리 CRUD | 운영자용 |
| 🟢 | 사용자 운동 패턴 분석 API | 큰 작업 |
| 🟢 | 데이터 기반 개인화 루틴 추천 | 가장 큰 작업, 알고리즘 설계 필요 |
| ⚪ | 운동 세트 개념 도입 | DB 컬럼·DTO 신설, 분석기와 협의 — [`project-squat-first`](../../../C:/Users/khjae/.claude/projects/E--init/memory/project_squat_first.md) 와 묶어 후순위 |

### 2-3. AI Server (FastAPI) — 🟢 변경 최소화 (정책)

[`feedback-minimize-python-changes`](../../../C:/Users/khjae/.claude/projects/E--init/memory/feedback_minimize_python_changes.md) 따라 거의 손대지 않음.

| 우선 | 항목 | 비고 |
|------|------|------|
| 🟢 | (필요 시) `ExtractReferenceData` 실제 구현 | 새 운동 추가 시점에 함께 — 현재는 `UNIMPLEMENTED` 응답. [`project-squat-first`](../../../C:/Users/khjae/.claude/projects/E--init/memory/project_squat_first.md) 따라 보류 |
| ⚪ | 런지·플랭크 분석기 추가 | 위와 같은 시점 |
| ⚪ | 운동 세트 자동 구분 분석 로직 | DB 스키마 결정 후 |

**현재로서는 사실상 변경 작업 없음.** 결합·신뢰성·thread-safety 까지 c7657f1 에서 완료.

### 2-4. Infra / Ops — 🟢 배포 시점에

| 우선 | 항목 | 위치 |
|------|------|------|
| 🟢 | A6 운영 알람 (Slack 웹훅 + Spring 헬퍼) | [`decisions/ai-backend-coupling.md`](../decisions/ai-backend-coupling.md) §4 |
| 🟢 | HTTPS 종료 + 도메인 | [`19-deployment.md`](../19-deployment.md) TODO |
| 🟢 | MySQL 호스트 노출 차단 (운영용) | 위와 동일 |
| 🟢 | DB 마이그레이션 도구 (Flyway 등) | 위와 동일 |
| 🟢 | dependabot 취약점 처리 | frontend npm `audit fix` 즉시, ai-server pip 신중 |

---

## 3. 우선순위 — 졸업 시연까지

가정: 시연 = 사용자가 카메라 켜고 스쿼트 5개 하면 → 실시간 자세 분석 + 종료 후 통계·리포트 표시.

### 🔴 즉시 (필수, 시연 핵심 흐름)

이게 없으면 시연 자체가 불가능:

1. **프론트 운동 시작/종료 API 호출** — `services/exerciseService.ts` 신설, `exercise.tsx` 의 녹화 버튼에 연결
2. **분기 H 결정** ([`decisions §5-β`](../decisions/ai-backend-coupling.md)) — 카메라 프레임 송신 경로. H1 (백엔드 프록시) 추천
3. **백엔드 프록시 `POST /exercises/sessions/{id}/frame`** — H1 채택 시
4. **프론트 카메라 프레임 송신** — H1 endpoint 호출
5. **운동 결과 화면** — 종료 후 rep·sync_rate·feedback 표시

→ 위 5개가 끝나면 시연용 한 사이클 완성. 다른 모든 회색 항목은 보너스.

### 🟡 단기 (시연 풍성하게)

- 캘린더·주간 통계 화면 데이터 연동
- TTS 재생 (피드백 멘트 발화)
- 관절 점 오버레이 시각화
- worst 구간 차트
- GPT/Claude AI 리포트 (졸업작품 어필 포인트)

### 🟢 장기 (운영 직전·이후)

- A6 운영 알람, HTTPS, MySQL 노출 차단, DB 마이그레이션 도구
- 관리자 화면들
- 운동 목표·달성 현황
- 사용자 운동 패턴 분석, 개인화 루틴 추천

### ⚪ 보류 (스쿼트 외 운동 추가 시점에)

- 런지·플랭크 분석기
- `ExtractReferenceData` 실제 구현
- 운동 세트 자동 구분

---

## 4. 한눈에 보는 의존성

```
┌─────────────────────────────────────────────────────────────┐
│ 분기 H 결정 (카메라 프레임 송신 경로)                          │
│  └─ H1 채택 ──► 백엔드 프록시 endpoint 신설                   │
└─────────────────────────┬───────────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ 프론트 services/exerciseService.ts 신설                      │
│  ├─ startSession()  POST /exercises/sessions                │
│  ├─ stopSession()   PUT  /exercises/sessions/{id}/stop      │
│  └─ sendFrame()     POST /exercises/sessions/{id}/frame    │ (H1 경로)
└─────────────────────────┬───────────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ exercise.tsx 의 녹화 버튼·카메라 프레임 콜백 연결              │
│  └─ DEV 패널 (수동 syncRate) 제거                            │
└─────────────────────────┬───────────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ 시연 한 사이클 완성: 카메라 → AI 분석 → DB 영속화 → 결과 화면   │
│  (이때부터 실증 e2e 가능 — 18-testing-guide §8)              │
└─────────────────────────────────────────────────────────────┘
```

---

## 관련 문서
- [`REQUIREMENTS.md`](../REQUIREMENTS.md) — 코드 기준 도메인별 요구사항
- [`architecture/ai-backend-integration.md`](../architecture/ai-backend-integration.md) — 결합 현황
- [`decisions/ai-backend-coupling.md`](../decisions/ai-backend-coupling.md) — 미결 분기 (H 포함)
- [`18-testing-guide.md`](../18-testing-guide.md) §8 — 시연 한 사이클 검증 절차
- [`19-deployment.md`](../19-deployment.md) — 배포 TODO
