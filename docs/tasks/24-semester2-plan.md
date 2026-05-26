# Backend 2학기 10주 계획

마지막 업데이트: 2026-05-24
범위: **백엔드(Spring) 작업자 1명 시점**의 2학기 10주 계획. 프론트엔드·AI·인프라는 다른 트랙(또는 다른 사람)이라 가정하고, 백엔드가 챙길 작업과 그 작업이 다른 트랙에 의존하는 지점만 다룬다.
연관: [`20-feature-roadmap.md`](./20-feature-roadmap.md), [`21-task-assignment.md`](./21-task-assignment.md), [`22-backend-tasks-detail.md`](./22-backend-tasks-detail.md)

> **이 문서는 가정 기반 계획**입니다. 방학 진척에 따라 출발점이 달라지면 주차 매핑이 한두 주 밀리거나 압축됨. Week 1에 실제 진척 점검 후 조정.

---

## 0. 출발 가정 (방학 종료 시점)

**완료됐다고 가정하는 것 (백엔드)**:
- ~~BE-01 (프록시 endpoint)~~ 🗑️ 폐기 — 분기 **H2 (프론트 → AI 직결)** 채택 (2026-05-24)
- BE-10 (AI 헬스체크 + Resilience4j Circuit Breaker, H2 부속)
- BE-11 (콜백 PoseData 검증 게이트, H2 부속)
- 분기 I (인증 토큰 흐름) I1 잠정 — `INTERNAL_API_TOKEN` 정적 공유
- BE-02 (worst 구간 보강)
- BE-03 (GPT/Claude 리포트, LLM 제공자 결정 완료)
- BE-04 A안 (카테고리 조회) — 1h 짜리 가벼운 안

**아직 안 됐다고 가정하는 것 (백엔드)**:
- BE-05 (관리자 통계)
- BE-06 (Goal 도메인)
- BE-07 (패턴 분석)
- BE-08 (추천)
- BE-09 (세트 도입) — AI-03 와 동시
- OP-01·04·07·08 등 백엔드가 손 닿는 운영 작업

> **만약 방학에 BE-01~03 도 못 끝냈다면**: Week 1~2 를 방학 잔여로 쓰고, 아래 계획을 2주씩 밀어서 8주 압축. Week 9·10 만 발표용으로 사수.

---

## 1. 주차별 계획 (백엔드)

### Week 1 — 회고 + 운영 기반

| 작업 | 추정 | 비고 |
|------|------|------|
| 1학기 시연 회고 (백엔드 관점 — 어디서 막혔는지, 결합 결정 이력 정리) | 2h | [`architecture/ai-backend-monthly-log.md`](../architecture/ai-backend-monthly-log.md) 업데이트 |
| OP-07 (CI — GitHub Actions PR 마다 `./gradlew test`) | 2h | 이후 회귀 방지 |
| OP-04 (Flyway 도입, 베이스라인 잡기) | 4h | Week 5 의 세트 컬럼 추가 때 깔끔하게 쓰기 위함 |
| Week 2 의 proto 변경 (BE-09) 을 AI 작업자와 일정 합의 | 30m | 양쪽 동시 PR 필요 |

**백엔드 마일스톤**: CI 동작, Flyway 베이스라인 확정.

---

### Week 2 — BE-09 proto 확장 + 운영 알람

| 작업 | 추정 | 비고 |
|------|------|------|
| BE-09 의 proto 확장 — `backend/src/main/proto/exercise.proto` 에 `PoseDataRequest.set_index/rep_index_in_set`, `CompleteRequest.total_sets/sets[]` 추가 | 2h | AI 측 proto 동시 변경 필요 (AI 작업자 일정) |
| `ExerciseGrpcService.java`, `PoseDataService.java` 에서 새 필드 받기/저장 (기본값 처리 — AI 가 아직 안 보내면 set_index=1) | 2h | 호환성 유지 |
| OP-01 (Slack 웹훅 + `AlertService` 헬퍼 + 감지 지점 3곳) | 3h | 사용자 테스트 시작 전 필수 |
| OP-08 (Spring Actuator 기본 + Prometheus 메트릭 노출) | 4h | Grafana 는 인프라 사람에게 위임 가능 |

**백엔드 마일스톤**: AI 가 set_index 보내면 받을 수 있는 상태. 운영 알람 동작.

---

### Week 3 — 운동 카탈로그 + Goal 도메인 시작

| 작업 | 추정 | 비고 |
|------|------|------|
| 운동 카탈로그 시드 — 런지 (`mysql/data.sql` 또는 Flyway 마이그레이션, 기준 좌표 등록 endpoint 호출) | 2h | AI-02 런지 분석기가 Week 3 안에 나온다는 가정 |
| BE-09 의 DB 컬럼 (`Session.setCount`, `PoseData.setIndex`) — Flyway 마이그레이션 | 2h | proto 만 했던 것을 영속까지 |
| BE-06 (Goal 도메인) 시작 — `model/member/Goal.java`, `GoalRepository`, `mysql/schema.sql` 컬럼 | 2h | |
| BE-06 — `GoalController` + 4 endpoint (POST/GET/PATCH/DELETE) | 3h | |

**백엔드 마일스톤**: 런지 시드 등록, BE-09 DB 까지 완성, Goal CRUD 동작.

---

### Week 4 — Goal 진척 자동 갱신 + 플랭크 카탈로그

| 작업 | 추정 | 비고 |
|------|------|------|
| BE-06 — `GoalService` 의 진척 자동 갱신: 세션 종료 콜백(`CompleteAnalysis` 핸들러) 에서 관련 Goal 의 `currentValue` 증가 | 3h | gRPC 콜백 흐름 변경, [`136f0e6 SessionTimeoutScheduler`](../architecture/ai-backend-monthly-log.md) 와 같은 자리 |
| BE-06 단위 테스트 — 주간 세션 목표 진척 시나리오 | 2h | |
| 운동 카탈로그 시드 — 플랭크 (rep 대신 hold_seconds 의미. 시드 데이터에 운동 종류별 단위 명시) | 2h | AI-02 플랭크 분석기 나오는 시점에 맞춤 |
| BE-09 + 플랭크 결합 검증 — 플랭크의 "1 rep = 1 hold" 가 set 카운트에서 어떻게 동작하는지 e2e 1회 | 2h | 의미 충돌 가능 지점 |

**백엔드 마일스톤**: Goal 진척이 실제로 갱신됨. 운동 3종 (스쿼트·런지·플랭크) 카탈로그 정착.

---

### Week 5 — 운영 인프라 + 사용자 테스트 준비

| 작업 | 추정 | 비고 |
|------|------|------|
| OP-02 (HTTPS + 도메인) — Spring 측에 reverse proxy 설정, `application.yml` 의 server 설정 | 2h | 인프라 사람이 nginx 잡으면 백엔드는 cert 경로만 |
| OP-03 (MySQL 호스트 노출 차단 — 운영용 `docker-compose.prod.yml` 신설) | 1h | |
| 사용자 테스트용 시드 보강 — 테스트 계정 5~10개, 초기 운동 카탈로그 검수 | 2h | |
| 백엔드 측 로깅 강화 — gRPC 콜백 실패·LLM 호출 실패·Goal 진척 실패를 모두 ERROR 로그로 (OP-01 알람과 연결) | 3h | 사용자 테스트 중 디버깅 시간 단축 |
| Week 6 모집 일정 확정 (참여자 5~10명, 동의서 문구) | 1h | |

**백엔드 마일스톤**: 외부 사용자 접속 가능한 HTTPS 환경. 디버깅용 로그·알람 망 완비.

---

### Week 6 — 1차 사용자 테스트 진행 + 관리자 통계

| 작업 | 추정 | 비고 |
|------|------|------|
| 1차 사용자 테스트 진행 (1주간 자유 사용) | 모니터링만 | 백엔드 작업자는 알람·로그 보면서 응급 핫픽스 대응 |
| BE-05 (관리자 통계 API 3개) — `GET /admin/stats/overview`, `/sessions`, `/exercises` | 4h | Member.role 확인 선행 (없으면 +1h) |
| BE-05 의 `AdminStatsService` + `SessionRepository` 집계 메서드 | 2h | |
| 사용자 테스트 응급 핫픽스 (예상 분량) | 4h+ | |

**백엔드 마일스톤**: 사용자 테스트 데이터 쌓이기 시작 (이때부터 BE-07/08 의 입력 데이터 생김). 관리자가 실시간 현황 봄.

> 위험: 핫픽스가 4h+ 를 넘으면 BE-05 를 Week 7 로 미루기.

---

### Week 7 — 1차 피드백 트리아지 + BE-07 패턴 분석

| 작업 | 추정 | 비고 |
|------|------|------|
| 1차 테스트 피드백 트리아지 (백엔드 측 이슈 분리) | 2h | |
| top 3~5 백엔드 이슈 수정 | 6h+ | DB 쿼리 성능, gRPC 콜백 누락, Goal 진척 오류 등 예상 |
| BE-07 (패턴 분석 3 endpoint) — `GET /patterns/periodicity`, `/intensity-trend`, `/consistency` | 6h | 단순 통계 안 (요일별 평균, 시간대별 분포) |
| `PatternAnalysisService` + Session 시계열 집계 메서드 | 2h | |

**백엔드 마일스톤**: 1차 피드백 반영 완료. 4주치 데이터(Week 3 ~ Week 7) 로 패턴 분석 의미 있는 결과 나옴.

---

### Week 8 — BE-08 추천 + 통합 검증

| 작업 | 추정 | 비고 |
|------|------|------|
| `decisions/recommendation-algorithm.md` 작성 (시연 후 LLM/협업필터링 업그레이드 여지 기록, 시연용은 규칙 기반 1안) | 1h | [`feedback-decision-doc`](../../../C:/Users/khjae/.claude/projects/E--init/memory/feedback_decision_doc.md) 적용 |
| BE-08 (추천 2 endmpoint) — 규칙 기반: 가장 약한 카테고리 + 최근 빈도 낮은 운동 보강 | 4h | BE-07 의 패턴 분석 결과를 입력 |
| `RoutineRecommendationService` + 추천 근거 한 줄 생성 로직 | 2h | "최근 하체 운동이 적어서" 같은 텍스트 |
| Goal·패턴·추천 통합 e2e 검증 — 한 사용자 시나리오로 처음부터 끝까지 | 3h | |
| Week 7 잔여 핫픽스 | 4h+ | |

**백엔드 마일스톤**: 실데이터 기반 패턴·추천 동작 — 발표 임팩트 큰 부분.

---

### Week 9 — 2차 사용자 테스트 + 운영 마감

| 작업 | 추정 | 비고 |
|------|------|------|
| 2차 사용자 테스트 진행 (3~5일, 1차 수정 검증 + 새 기능 검수) | 모니터링만 | |
| 2차 발견 백엔드 이슈 처리 | 6h+ | |
| DB 백업 절차 정립 + 1회 백업 실행 (운영 첫 사용자 데이터 보호) | 2h | `mysqldump` 크론 + 보관 정책 |
| 백엔드 코드 cleanup — TODO 정리, 미사용 controller/dto 삭제, deprecated `/complete` endpoint 최종 제거 검토 | 4h | |
| Flyway 마이그레이션 일원화 점검 (방학~Week 5 사이 누적된 schema 변경) | 2h | |

**백엔드 마일스톤**: 발표 시연 직전 안정화. 회귀 없음 보장.

---

### Week 10 — 발표 산출물 (백엔드 분담분)

| 작업 | 추정 | 비고 |
|------|------|------|
| 최종 보고서 — 백엔드 아키텍처 도식, 결합 결정 이력 (현 `architecture/ai-backend-*.md` 통합·요약) | 8h | |
| 발표 PPT — 백엔드 슬라이드 (gRPC 결합·인증·신뢰성·Goal/패턴/추천) | 4h | 프론트/AI 슬라이드는 각 담당이 |
| 시연 영상 — 백엔드 API 응답 부분 (관리자 화면, 패턴/추천 결과) 캡처/녹화 협조 | 2h | |
| 심사 Q&A 준비 — 결합 결정·proto 동기·인증 경로 등 백엔드 단골 질문 답안 | 2h | |
| 리허설 2회 | 2h | |

**백엔드 마일스톤**: 졸업 심사 완료.

---

## 2. 마일스톤 요약 (백엔드)

| 주차 끝 | 백엔드 상태 |
|---------|------|
| Week 1 | CI 동작, Flyway 베이스라인 |
| Week 2 | BE-09 proto 받는 상태, 운영 알람 동작 |
| Week 4 | Goal 진척 자동 갱신, 운동 3종 카탈로그 |
| Week 5 | HTTPS, 로깅·알람 망 완비 |
| Week 6 | 사용자 테스트 1차, BE-05 동작 |
| Week 7 | 1차 피드백 반영, BE-07 패턴 동작 |
| Week 8 | BE-08 추천 동작 (실데이터 기반) |
| Week 9 | 2차 테스트 완료, 발표 직전 안정화 |
| Week 10 | 졸업 심사 |

---

## 3. 다른 트랙 의존 지점

백엔드만 한다고 해도 다른 트랙(AI·프론트·인프라) 일정에 막히는 지점:

| 주차 | 의존 | 막히면 |
|------|------|--------|
| Week 2 | AI 측 proto 동기 변경 | BE-09 의 set 필드가 한쪽만 변경되면 핸드쉐이크 실패 → 같은 주에 같이 머지 |
| Week 3~4 | AI-02 런지·플랭크 분석기 | 카탈로그 시드 등록해도 실제 분석 안 됨 → Week 5 까지 늦어지면 운동 종류 확장 발표 임팩트 약화 |
| Week 5 | OP-02 nginx/도메인 (인프라 사람) | HTTPS 안 되면 외부 사용자 테스트 불가 → Week 6 모집 연기 |
| Week 6~7 | 프론트 측 새 운동 UI, Goal 화면 | 백엔드 API 가 있어도 사용자 테스트에서 못 씀 → 백엔드 작업자가 Postman 컬렉션이라도 만들어 직접 시연 검증 |
| Week 8 | 프론트 측 패턴·추천 위젯 | 화면 없으면 발표용 캡처 부족 → 백엔드 작업자가 JSON 응답 prettify 해서 슬라이드에 직접 보여주기 |

**대응 원칙**: 다른 트랙 막혀도 백엔드 작업은 멈추지 않음. API 만 살아 있으면 발표는 가능 (Postman/Swagger 로 시연 가능). 다만 사용자 테스트 가치는 떨어짐.

---

## 4. 리스크 (백엔드 한정)

| 리스크 | 영향 | 대응 |
|--------|------|------|
| Member 에 role/admin 필드 없음 (BE-05 시작 시 발견) | Week 6 슬립 1일 | Week 5 의 사용자 테스트 시드 작업 때 동시 확인, 없으면 +1h 로 추가 |
| Goal 진척 자동 갱신이 gRPC 콜백 흐름과 충돌 (낙관적 락 / `@Version` 이슈) | Week 4 슬립 1~2일 | 기존 `SessionTimeoutScheduler` 의 패턴(낙관적 락 + 재시도) 그대로 복제 |
| LLM 단가 폭주 (사용자 테스트 중 BE-03 가 매 세션 호출) | 비용 발생 | 1세션당 1회 호출 강제 + 월 한도 설정. 초과 시 Report 비워두기 |
| 핫픽스 양 폭주 (Week 6·7·8 합계 14h+ 가능) | BE-07/08 슬립 | Week 8 의 BE-08 을 "단순 카테고리 통계 추천" 으로 격하 (2h 안) |
| Flyway 도입 후 기존 `schema.sql` 과의 충돌 | Week 1 슬립 | Flyway baseline 모드 사용, 기존 스키마는 V1 으로 캡처 |
| proto 변경 후 AI 측 사본 동기 지연 | Week 2 슬립 | proto 변경은 항상 양쪽 동시 PR — 백엔드 작업자가 AI 작업자에게 알림 의무 |

---

## 5. 작업량 합계 (백엔드만)

| 분야 | 추정 시간 (h) |
|------|------------|
| BE-05·06·07·08 합계 | ~27h |
| BE-09 (proto + DB) | ~6h |
| 운동 카탈로그 시드 (런지·플랭크) | ~4h |
| 운영 (OP-01·02·03·04·07·08) | ~17h |
| 사용자 테스트 백엔드 대응 (로깅·시드·핫픽스 1·2차) | ~18h |
| Goal 진척 자동 갱신 + 통합 검증 | ~5h |
| 발표 산출물 (백엔드 분담분) | ~18h |
| 회고·결정 문서·cleanup | ~6h |
| **합계** | **~101h** |

→ 학기 중 백엔드 작업자 1명 가용 시간을 주당 8h × 10주 = 80h 로 보면 **20h 초과**. Week 1~5 에 압축하고 Week 6 이후 핫픽스·운영 모드로 가는 게 안전.

대응: BE-08 을 단순 격하(2h) + 발표 산출물 일부를 Week 9 로 분산하면 90h 미만으로 떨어짐.

---

## 6. Week 1 안에 받아야 할 결정 (백엔드 관련)

| 결정 | 의존 | 추천 |
|------|------|------|
| ~~분기 H 결정~~ | ~~Week 1~~ | ✅ **H2 (직결) 채택** (2026-05-24, 방학 진입 전 확정) |
| 분기 I (인증 토큰 흐름) 운영 단계 I2 전환 시점 | 사용자 수 N 이상 또는 베타 종료 시 | I1 정적 공유 잠정, 운영 전 I2 검토 |
| AI 작업자와 BE-09 proto 동기 일정 | Week 2 | 같은 주 같은 PR 머지 |
| LLM 호출 정책 (1세션 1회 vs 무제한) | Week 5 BE-03 점검 | 1세션 1회 + 월 한도 $20 |
| BE-08 추천 알고리즘 (규칙 기반 vs LLM) | Week 8 | 규칙 기반 (시연용 충분, 비용 0) |
| BE-04 enum 유지(A) vs 엔티티 전환(B) — 1학기에 결정 못 받았다면 | Week 6 관리자 UI 시점 | A 유지 (B 는 영향 범위 큼) |
| Member.role 필드 — 신설 시점 | Week 5 또는 Week 6 시작 시 | 사용자 테스트 시드와 묶어 Week 5 |

---

## 관련 문서
- [`21-task-assignment.md`](./21-task-assignment.md) — 전체 작업 목록 (스택별, 프론트·AI·인프라 트랙 포함)
- [`22-backend-tasks-detail.md`](./22-backend-tasks-detail.md) — BE 작업 상세 풀이 (BE-02~12, BE-01 폐기)
- [`20-feature-roadmap.md`](./20-feature-roadmap.md) — PPT 요구사항 ↔ 코드 매핑
- [`../decisions/ai-backend-coupling.md`](../decisions/ai-backend-coupling.md) — 결합 결정 이력
- [`../architecture/ai-backend-monthly-log.md`](../architecture/ai-backend-monthly-log.md) — 1학기 결합 작업 이력 (회고·보고서 자료)
