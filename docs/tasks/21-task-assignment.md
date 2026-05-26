# 작업 분배표

마지막 업데이트: 2026-05-23
근거: [`20-feature-roadmap.md`](./20-feature-roadmap.md) 의 스택별 남은 작업을 사람 분배 가능한 단위로 쪼갠 결과.

읽는 법:
- **상태**: 📋 대기 / 🚧 진행 중 / ✅ 완료 / 🟦 보류 / 🟥 차단
- **우선**: 🔴 즉시 (시연 핵심) / 🟡 단기 (시연 풍성) / 🟢 장기 / ⚪ 보류
- **추정**: 코드 변경 분량 기준 *대략* (실제 디버깅 시간은 별도)
- **의존성**: 이 작업이 시작되려면 먼저 완료되어야 하는 다른 작업

---

## 1. Frontend (React Native) — 가장 큰 갭

| ID | 작업 | 우선 | 의존성 | 추정 | 담당 | 상태 |
|----|------|-----|--------|------|------|------|
| FE-01 | `services/exerciseService.ts` 신설 (startSession / stopSession) + `types/exercise.ts` | 🔴 | — | 1~2h | | 📋 |
| FE-02 | `exercise.tsx` 녹화 버튼 핸들러 — 시작 시 startSession, 종료 시 stopSession 호출 | 🔴 | FE-01 | 2h | | 📋 |
| FE-03 | `exercise.tsx` 의 DEV 패널(수동 syncRate) 제거 | 🔴 | FE-02 | 30m | | 📋 |
| FE-04 | 카메라 프레임 캡처·base64 인코딩 로직 (`expo-camera` `takePictureAsync` 또는 frame callback) | 🔴 | — (H2 확정) | 3h | | 📋 |
| FE-05 | 프레임 송신 — `exerciseService.sendFrame()` → **AI `POST /pose` 직접 호출** (H2 채택). `Authorization: Bearer ${INTERNAL_API_TOKEN}` 헤더 첨부 (분기 I1 잠정) | 🔴 | FE-04, 분기 I 확정, AI 측 인증 미들웨어 | 2h | | 📋 |
| FE-06 | 운동 결과 화면 — 종료 후 rep 수·sync_rate·feedback 표시 | 🔴 | FE-02 | 4h | | 📋 |
| FE-07 | TTS 재생 — `expo-speech` + `/preferences/tts` + `/exercises/{id}/feedback-templates` 매핑 | 🟡 | FE-02 | 3h | | 📋 |
| FE-08 | 관절 점 오버레이 시각화 — AI 응답의 landmarks 좌표로 카메라 위에 점 그리기 | 🟡 | FE-04 | 4h | | 📋 |
| FE-09 | 캘린더 화면 데이터 연동 — `GET /records/calendar` | 🟡 | — | 2~3h | | 📋 |
| FE-10 | 주간 통계 화면 데이터 연동 — `GET /reports/weekly` 또는 SessionService API | 🟡 | — | 2~3h | | 📋 |
| FE-11 | 리포트 상세 화면 — worst 구간·이전 기록 비교·자세 분석 차트 | 🟡 | BE-02 | 5~6h | | 📋 |
| FE-12 | 운동 타이머 UI — 진행 시간 표시 | 🟡 | FE-02 | 1h | | 📋 |
| FE-13 | 관리자 화면 — 대시보드·카테고리·운동 영상 관리 | 🟢 | BE-03~05 | 8h+ | | 📋 |

소계: 🔴 6개, 🟡 6개, 🟢 1개

---

## 2. Backend (Spring Boot)

> **각 작업의 구체 풀이(현재 코드 상태·만질 파일·완료 기준·리스크)**: [`22-backend-tasks-detail.md`](./22-backend-tasks-detail.md)

| ID | 작업 | 우선 | 의존성 | 추정 | 담당 | 상태 |
|----|------|-----|--------|------|------|------|
| ~~BE-01~~ | ~~(H1 채택 시) `POST /exercises/sessions/{id}/frame` 프록시 endpoint~~ | ⚪ | — | — | | 🗑️ 폐기 (H2 채택, 2026-05-24) |
| BE-02 | worst 구간 선정 서비스 로직 보강 — `WorstSectionDto`·`SessionReportResponseDto` 채우는 메서드 | 🟡 | — | 3h | | 📋 |
| BE-03 | GPT/Claude 리포트 자동 생성 — `GptFeedbackService` 신설 (이미 env 에 `OPENAI_API_KEY`) | 🟡 | — | 6h | | 📋 |
| BE-04 | 카테고리 관리 CRUD API — `AdminCategoryController` | 🟢 | — | 3h | | 📋 |
| BE-05 | 관리자 대시보드 통계 API — 사용자/세션 집계 | 🟢 | — | 4h | | 📋 |
| BE-06 | 운동 목표 엔티티·CRUD API — `Goal` 도메인 신설 | 🟢 | — | 5h | | 📋 |
| BE-07 | 사용자 운동 패턴 분석 API — 주기성·강도 추세 | 🟢 | 데이터 축적 후 | 8h+ | | 📋 |
| BE-08 | 개인화 루틴 추천 API — 알고리즘 설계 필요 | 🟢 | BE-07 | 10h+ | | 📋 |
| BE-09 | 운동 세트 개념 도입 — DB 컬럼·DTO·gRPC 메시지 추가 ([`project-squat-first`](../../../C:/Users/khjae/.claude/projects/E--init/memory/project_squat_first.md) 와 협의) | ⚪ | 새 운동 추가 시점 | 5h | | 🟦 |
| **BE-10** | AI gRPC 헬스체크 + Resilience4j Circuit Breaker (H2 채택 부속) | 🔴 | H2 확정 | 4h | | 📋 |
| **BE-11** | 콜백 PoseData 검증 게이트 (H2 채택 부속) | 🔴 | H2 확정 | 3h | | 📋 |
| **BE-12** | 콜백 처리 Outbox 패턴 (H2 채택 부속, 운영 신뢰성) | 🟡 | BE-11 | 5h | | 📋 |
| **BE-30** | TTS 피드백 효과 분석 (포폴 어필용, BE-07 와 묶기) | 🟢 | BE-07, 데이터 4주 축적 | 4h | | 📋 |

소계: 🔴 2개, 🟡 3개, 🟢 6개, ⚪ 2개 (BE-01 폐기 + BE-09 보류)

---

## 3. AI Server (FastAPI) — 거의 없음

[`feedback-minimize-python-changes`](../../../C:/Users/khjae/.claude/projects/E--init/memory/feedback_minimize_python_changes.md) 정책으로 손대지 않음. **현재 시연용 동작에는 추가 작업 없음.**

| ID | 작업 | 우선 | 의존성 | 추정 | 담당 | 상태 |
|----|------|-----|--------|------|------|------|
| AI-01 | `ExtractReferenceData` 실제 구현 — YouTube 다운로드 + MediaPipe 추출 | ⚪ | 새 운동 추가 시점 | 6h | (원작자) | 🟦 |
| AI-02 | 런지·플랭크 분석기 추가 | ⚪ | 위와 동일 | 운동당 4h+ | (원작자) | 🟦 |
| AI-03 | 운동 세트 자동 구분 분석 | ⚪ | BE-09 와 협의 | 4h | (원작자) | 🟦 |

소계: ⚪ 3개 (모두 보류)

---

## 4. Infra / Ops — 배포 시점

| ID | 작업 | 우선 | 의존성 | 추정 | 담당 | 상태 |
|----|------|-----|--------|------|------|------|
| OP-01 | A6 운영 알람 — Slack 웹훅 + Spring `AlertService` 헬퍼 + 감지 지점 3곳 | 🟢 | 배포 직전 | 3h | | 📋 |
| OP-02 | HTTPS 종료 + 도메인 (`api.shadowfit.com`?) | 🟢 | 도메인 발급 | 2h+ | | 📋 |
| OP-03 | MySQL 호스트 노출 차단 (운영용 `docker-compose` 분리) | 🟢 | — | 1h | | 📋 |
| OP-04 | DB 마이그레이션 도구 (Flyway) 도입 | 🟢 | — | 4h | | 📋 |
| OP-05 | dependabot — frontend npm `audit fix` (axios·@xmldom/xmldom 등) | 🟡 | — | 30m | | 📋 |
| OP-06 | dependabot — ai-server pip 신중 업그레이드 (`python-multipart`, `protobuf`) | 🟡 | proto 호환성 확인 | 1~2h | | 📋 |
| OP-07 | CI 도입 — GitHub Actions 로 PR 마다 `./gradlew test` | 🟢 | — | 2h | | 📋 |
| OP-08 | 모니터링 스택 — Spring Actuator + Prometheus + Grafana | 🟢 | — | 6h+ | | 📋 |

소계: 🟡 2개, 🟢 6개

---

## 5. 미결 결정 (작업 시작 전 답 필요)

각각 [`decisions/ai-backend-coupling.md`](../decisions/ai-backend-coupling.md) §해당 절에 트레이드오프 정리됨.

| 결정 | 막는 작업 | 상태 |
|------|---------|------|
| ~~**분기 H** 카메라 프레임 송신 경로~~ | ~~BE-01, FE-04, FE-05~~ | ✅ H2 채택 (2026-05-24) |
| **분기 I** 인증 토큰 흐름 (H2 부속) | FE-05, AI 측 인증 미들웨어, BE-10/11 | ✅ I1 정적 공유 잠정 (2026-05-24), 운영 단계에서 I2 검토 |
| **분기 B** proto 단일 소스 (루트 `proto/`) | (없음, 별도 정리 작업) | B3 또는 B1 유지 |
| **분기 D** AI 세션 in-memory vs 외부화 | (멀티 인스턴스 필요 시) | D1 (in-memory) |
| TTS 알람 채널 (Slack/이메일/SaaS) | OP-01 | A6-CH-1 (Slack 웹훅) |

---

## 6. 의존성 그래프 — 시연까지의 Critical Path

분기 H = H2 채택 결과 그래프 (백엔드 프록시 없음):

```
              [AI 측 인증 미들웨어 추가] ◀── AI 담당자 (H2 부속)
                          │
                          ▼
   [FE-01]────►[FE-02]──►[FE-04]──►[FE-05]──┐
   service     녹화 핸들러   프레임 캡처    AI 직접 송신  │
                                              │
                  ┌───────────────────────►[FE-06]
                  │ (콜백 → DB → 조회)        결과 화면
                  │                              │
              [백엔드 콜백 처리]                  ▼
              (BE-10·11 동시 진행)        ━━━━━━━━━━━━━━
                                         🎯 시연 가능 시점
                                         ━━━━━━━━━━━━━━
```

Critical path = 약 6개 작업 (AI 인증 미들웨어 + FE-01·02·04·05·06) + BE-10·11 병렬.
**작업 총량 약 15~17시간**. 두 명이 병렬로 가면 1주일, 한 명이면 2주.

---

## 7. 병렬화 가능한 그룹

같은 시점에 여러 사람이 동시 작업 가능한 묶음:

| 그룹 | 작업 | 누구 |
|------|------|------|
| A (즉시 시작) | FE-01, FE-02, FE-03 | 프론트 1명 |
| A (병렬) | BE-02 (worst 구간) | 백엔드 1명 |
| A (병렬) | OP-05 (npm audit fix) | DevOps 1명 |
| A (병렬) | **AI 측 인증 미들웨어 (H2 부속, ~2h)** | AI 담당자 |
| B (FE-01~03 완료 후) | **BE-10 (헬스체크+CB), BE-11 (콜백 검증)**, FE-04 (프레임 캡처) | 분담 |
| B (병렬) | FE-09, FE-10 (캘린더·통계 화면) | 프론트 2명째 |
| C (B 완료 후) | FE-05 (AI 직접 송신 연결), FE-06 (결과 화면) | 프론트 |
| C (병렬) | BE-03 (GPT 리포트), FE-07 (TTS) | 백엔드 + 프론트 |
| D (시연 직후) | OP-01 (알람), OP-02 (HTTPS), OP-03 (MySQL 차단), **BE-12 (Outbox)** | 인프라 + 백엔드 |

---

## 8. 권장 다음 액션 (1주차)

1. ~~**분기 H 결정**~~ ✅ H2 확정 (`decisions §11` 결정 로그)
2. **AI 담당자에게 H2 결정 + 인증 미들웨어 작업 알림** (분기 I1 잠정)
3. **FE-01·02·03 + BE-10·11 동시 진행**
4. **FE-04·FE-05·FE-06 순서대로 마무리**
4. **수동 e2e 1회** ([`18-testing-guide.md`](../18-testing-guide.md) §8)
5. 시연 가능 시점 도달 후 🟡 작업들 분배

---

## 관련 문서
- [`20-feature-roadmap.md`](./20-feature-roadmap.md) — PPT 요구사항 ↔ 코드 매핑
- [`decisions/ai-backend-coupling.md`](../decisions/ai-backend-coupling.md) — 미결 분기 트레이드오프
- [`18-testing-guide.md`](../18-testing-guide.md) §8 — 수동 e2e 절차
- [`architecture/ai-backend-integration.md`](../architecture/ai-backend-integration.md) — 결합 현황
