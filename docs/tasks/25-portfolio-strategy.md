# 포폴·진로 전략 회고

마지막 업데이트: 2026-05-24
범위: 졸업작품을 백엔드 신입 포폴로 어떻게 발전시킬지 논의한 결과 정리. 결정·미결·산출물·다음 액션.
연관: [`20-feature-roadmap.md`](./20-feature-roadmap.md), [`21-task-assignment.md`](./21-task-assignment.md), [`24-semester2-plan.md`](./24-semester2-plan.md), [`../architecture/ai-backend-monthly-log.md`](../architecture/ai-backend-monthly-log.md)

> **이 문서는 살아 있는 회고**. 새 결정·새 방향이 나오면 누적 업데이트. 시점 의존 정보(git status 등)는 안 적음.

---

## 0. 전제 (메모리에 박힌 것)

- [`project-two-semester-schedule`](../../../C:/Users/khjae/.claude/projects/E--init/memory/project_two_semester_schedule.md) — 1학기 MVP → 방학 3개월 → 2학기 10주
- [`user-career-target`](../../../C:/Users/khjae/.claude/projects/E--init/memory/user_career_target.md) — **백엔드(Spring) 포지션 지원**, AI 아님. 풀스택 추천 X
- [`project-squat-first`](../../../C:/Users/khjae/.claude/projects/E--init/memory/project_squat_first.md) — 현재 스쿼트만. 운동 확장은 2학기 콘텐츠
- [`feedback-minimize-python-changes`](../../../C:/Users/khjae/.claude/projects/E--init/memory/feedback_minimize_python_changes.md) — ai-server 는 다른 사람. 결합·인증·콜백 영역만 본인 어필 가능

---

## 1. 주요 논의 주제와 결론

### 진로 전략

| 주제 | 결론 |
|------|------|
| **백엔드 신입 시장에서 이 프로젝트 위치** | 양적 평균 이상 (API 30~40개·도메인 8~10개 예상), **다양성 부족** (S3·OAuth2·결제·푸시·검색 등 외부 통합 흔한 항목 0개). 깊이(gRPC 결합·신뢰성·결정 이력)는 평균 두 단계 위 |
| **Spring만 한 거 양 적은가** | 합격선 충분히 넘음. "기능 카운트" 어필이 약해 보이는 게 사실 → 다양성 보완 또는 깊이 어필로 채움 |
| **Spring 단독 제약** | 양쪽 필요 작업(OpenTelemetry·gRPC bidi·mTLS) 빼고도 60% 가능. **"책임 경계 의식"이 오히려 시니어 시그널** |
| **풀스택으로 갈까** | ❌ 비추. 백엔드 깊이 추가가 시간 대비 가성비 훨씬 좋음. "여러 스택 어설프게" 인상 위험 |
| **JVM 튜닝** | 신입 합격선 안 해도 됨. AWS·Redis 완료 후 차별화용 1주 정도 |

### 2026년 신입 시장의 변화 (Claude 시대)

| 주제 | 결론 |
|------|------|
| **AI로 코드 다 짜주는 시대 변별력** | 포폴 산출물 자체로는 변별력 ↓. **현장 실시간 시그널** (라이브 코딩·CS 즉답·자기 코드 100% 이해·디버깅·시스템 디자인 즉석)로 이동 |
| **AI 추천 = 최적인가** | **AI = 합격선 80%**. 나머지 20%가 신입 vs 시니어 가름. 측정·발견·"안 하기로 한 결정"·트레이드오프 가중치·비용-가치 판단·회귀 위험·실패 패턴 학습이 사람의 영역 |
| **올바른 AI 활용 방식** | AI로 짜고 → PR 리뷰하듯 한 줄씩 검증 → "왜 이렇게? 단점은? 대안은?" 셀프 질문 → 본인 측정으로 추천 검증 → 안 맞으면 가설·조정·회고 문서 |
| **트러블슈팅 스토리 정련** | docs 에 흩어진 트러블 5~7개를 면접 답변 형식(30초/2분/10분)으로 정련. 다만 "본인이 실제 한 작업"만. AI-server 측 트러블은 본인 작업 여부 git blame 으로 검증 필요 |

### 학습·작업 방식

| 주제 | 결론 |
|------|------|
| **코드 + CS 결합 학습** | 3단 답변(정의+본인 코드 예시+트레이드오프)이 가장 강함. **CS 50토픽 × 4칸(정의/코드 위치/트레이드오프/면접 답변 30초)** 표 작성, 매주 5개씩 채우기 |
| **수치 개선 사례** | 거의 필수가 됨. Tier 1(N+1·인덱스·Redis 캐싱) 1.5주면 3개 확보. **함정**: 인위적 비교·인위적 개악 후 개선·숫자만 외움·측정 방법 모름·동일 환경 비교 아님 |
| **한 API 깊이 vs 한 기능 깊이** | 둘 다 조합이 최강. **한 기능(운동 세션 lifecycle) 전체 깊이 + 그 안의 한 API(GET /reports/{id}) v1→v7 응축 진화기** |
| **수치 개선 추천 패키지** | v1 → v7 진화로 응답시간 850ms → 12ms(98.6% 감소) 만들기. 각 단계 AI 추천 + 본인 측정 + 발견/조정 회고 |

---

## 2. 깊이 작업 영역 — 후보 정리

### Spring 단독 가능 vs 양쪽 필요

| 작업 | Spring 단독? | 비고 |
|------|-----------|------|
| OpenTelemetry 분산 추적 | ❌ | FastAPI 측 instrumentation 필요. 단 Spring 측만 trace id 발행은 가능 |
| gRPC bidi 스트리밍 | ❌ | FastAPI 서비서 변경 필수 |
| Contract testing (Pact) | 🟡 | Spring 측 consumer 테스트만 가능 |
| mTLS | ❌ | 양쪽 cert |
| **Resilience4j (Circuit Breaker + Bulkhead)** | ✅ | client 측 |
| **장애 시뮬레이션 + Post-mortem** | ✅ | FastAPI 강제 종료만 |
| **SLO/SLI + Grafana** | ✅ | Spring 측 메트릭 |
| **Graceful shutdown** | ✅ | |
| **구조화 로깅 + correlation id** | ✅ | metadata 로 trace id 전파 |
| AI 세션 외부화 (Redis) | ❌ | `session_state.py` 가 FastAPI 측 |
| **PoseData 파티셔닝** | ✅ | Spring + MySQL |
| **Read replica + 읽기 분리** | ✅ | `AbstractRoutingDataSource` |
| **분산 락 (Redlock)** | ✅ | Goal 진척 등 |
| **HikariCP 튜닝** | ✅ | |
| **LLM semantic 캐싱** | ✅ | `GptFeedbackService` |
| **LLM Fallback chain + 토큰 모니터링** | ✅ | |
| **응답 품질 자동 평가 (LLM-as-judge)** | ✅ | |
| 모델 Shadow deployment | 🟡 | FastAPI 가 분석기 N개 띄워주면 Spring 측 라우팅으로 |

→ **Spring 단독으로 갈 수 있는 게 60%**. 운영 신뢰성·LLM 운영·DB 확장성 영역

### DB 영역 — 본 프로젝트 강점 살리는 작업

본 프로젝트 DB 특성: 시계열 대용량(PoseData) + JSON 컬럼 + 외부 콜백 영속 + 동시성

| 작업 | 어필 강도 | 신입 흔함도 | 본 프로젝트 시너지 |
|------|---------|----------|---------------|
| **PoseData 파티셔닝** (월 단위 Range) | 🔴 | 거의 안 함 | ⭐⭐⭐ |
| **JSON Generated column 인덱싱** | 🔴 | 거의 안 함 | ⭐⭐⭐ |
| **TTL/아카이빙 정책** (3개월 → S3 이관) | 🔴 | 거의 안 함 | ⭐⭐⭐ |
| **Zero-downtime 마이그레이션** (BE-09 setCount) | 🔴 | 거의 안 함 | ⭐⭐⭐ |
| **Deadlock 재현 + post-mortem** | 🔴 | 드뭄 | ⭐⭐⭐ |
| **Continuous aggregate / Materialized view** | 🔴 | 거의 안 함 | ⭐⭐ |
| **분산 락 (Redlock)** | 🔴 | 드뭄 | ⭐⭐ |
| **Read replica + lag 처리** | 🔴 | 드뭄 | ⭐⭐ |
| **인덱스 전략 시리즈 (5건 EXPLAIN)** | 🔴 | 단골 | ⭐⭐ |
| **MVCC + 격리 수준 실험** | 🔴 | 거의 안 함 | ⭐⭐ |

### RealMySQL 적용 실험 패키지

본 프로젝트 데이터 특성에 RealMySQL 챕터별 실험 적용:

| 챕터 | 본 프로젝트 적용 | 핵심 실험 |
|------|-------------|--------|
| Ch.4·5 (InnoDB·트랜잭션·잠금) | Goal 갱신 동시성, MVCC 동작 관찰 | MVCC·Lock 종류·낙관적 vs 비관적 throughput |
| Ch.8 (인덱스) | PoseData PK 설계, covering index | 클러스터링 인덱스 효과·B-Tree 깊이 |
| Ch.9·11 (옵티마이저·쿼리) | EXPLAIN 5건, 페이지네이션 | 옵티마이저 잘못된 선택 교정·offset→cursor |
| Ch.13 (파티셔닝) | PoseData Range 월 단위 | 1억 row 시뮬레이션·pruning 효과 |
| Ch.16 (복제) | Read replica + 읽기 분리 | replication lag 처리·read-after-write |
| Ch.18 (운영) | Slow query log + 모니터링 | pt-query-digest·HikariCP 메트릭 |

각 실험은 4단 문서화 (책 요약 / 가설 / 실험 설계 / 결과 + 회고) = STAR 면접 답변 그대로

---

## 3. 추천 통합 패키지

### 7주 통합 패키지 — 한 기능 깊이 + RealMySQL + Spring 운영

방학 후반 ~ 2학기 중반에 분산 진행. 가장 강한 어필 조합.

| Week | 작업 | 한 API 진화 (`GET /reports/{id}`) |
|------|------|-------------|
| 1 | 운동 세션 lifecycle 기능 전체 baseline 측정 + 측정 도구 셋업 (k6·p6spy·Grafana) | v1 baseline 측정 |
| 2 | RealMySQL Ch.4·5·8 실험 (MVCC·Lock·인덱스) | v2 (N+1 해결) + v3 (인덱스) |
| 3 | Redis 캐싱 도입 (cache-aside + stampede 방지) | v4 (캐싱) |
| 4 | LLM 비동기 + Fallback chain + semantic 캐싱 | v5 (LLM 비동기) |
| 5 | 동시성 + 분산 락 (Redlock) + Deadlock 재현 | v6 (Goal·Session 동시성) |
| 6 | SLO 정의 + 부하 테스트 + 통합 테스트 | v7 (SLO + 관측) |
| 7 | 회고 문서 + case-study 작성 + 면접 답변 정련 | 진화기 마무리 |

**최종 어필**: *"GET /reports/sessions API를 7번 진화시키며 응답시간 850ms → 12ms (98.6% 감소). 각 단계 RealMySQL 기반 인덱스·MVCC·트랜잭션 실험, Redis 캐싱·LLM 비동기 fallback, 분산 락·SLO 정의까지. 모든 단계 측정 데이터 + 트레이드오프 회고 문서 보관."*

---

## 4. 누적된 결정 사항 (아직 답 안 받음)

### 코드 작업 관련
| 결정 | 막는 작업 | 상태 |
|------|---------|------|
| ~~분기 H (프록시 vs 직결)~~ | ~~BE-01, FE-04·05~~ | ✅ **H2 (직결) 채택** (2026-05-24) — 성능(한 홉) + 백엔드 부하 분산 + PPT 일관성. BE-01 폐기, BE-10·11·12 신설 |
| 분기 I (인증 토큰 흐름, H2 부속) | FE-05, AI 측 인증 미들웨어 | ✅ I1 정적 공유 잠정 (2026-05-24), 운영 단계 I2 검토 |
| LLM 제공자 (OpenAI vs Anthropic) | BE-03 | `decisions/llm-provider.md` 작성 후 |
| BE-04 enum 유지(A) vs 엔티티 전환(B) | BE-04 | A (1h) |
| BE-08 추천 알고리즘 | BE-08 | 규칙 기반 (1~2h) |

### 방학·포폴 관련
| 결정 | 영향 |
|------|------|
| 베타 사용자 5~10명 모집 가능성 | 포폴 "베타 데이터" 카드 |
| 라이브 데모 호스팅 비용 (월 1~2만 원) 감당 의향 | AWS 실배포 |
| 시연 영상 vs 라이브 데모 우선 | 방학 Month 3 작업 |

### 깊이 트랙 선택
| 결정 | 후보 |
|------|------|
| Spring 단독 깊이 조합 | A' (운영 신뢰성+AI 운영) / B' (DB·확장성) / C' (보안·배치) / **D' (균형형 5주)** |
| DB 깊이 조합 | A (시계열+JSON) / B (동시성) / C (운영 종합) / **D (통합 5주)** |
| RealMySQL 패키지 | A (Ch.4·5·8 핵심 3주) / B (5주 깊이) / C (운영) |
| 한 API 깊이 대상 | `GET /reports/sessions/{id}` (다양성) vs `POST /sessions/{id}/frame` (시연 직결) |
| 한 기능 깊이 대상 | **운동 세션 lifecycle** (최강 후보) vs Goal vs 리포트 |

---

## 5. 산출물 제안 — 만들기 대기 중

방향 정해지면 작성할 수 있는 문서들. 우선순위 순:

| 우선 | 산출물 | 용도 |
|------|--------|------|
| 🔴 | `docs/tasks/19-vacation-plan.md` | 방학 3개월 백엔드 시점 plan |
| 🔴 | `docs/portfolio/case-study-reports-api.md` (or frame) | 한 API v1~v7 진화기 |
| 🔴 | `docs/portfolio/realmysql-experiments.md` | RealMySQL 12개 실험 4단 템플릿 |
| 🟡 | `docs/portfolio/case-study-exercise-session-lifecycle.md` | 한 기능 전체 진화기 |
| 🟡 | `docs/portfolio/perf-cases.md` | 수치 개선 사례 5개 4단 형식 |
| 🟡 | `docs/portfolio/cs-project-mapping.md` | CS 50토픽 × 4칸 학습 노트 |
| 🟡 | `docs/portfolio/ai-vs-measurement-discoveries.md` | AI 추천과 실측 차이 사례 |
| 🟢 | `docs/portfolio/db-deep-dive.md` | DB 영역 깊이 작업 템플릿 |
| 🟢 | `docs/portfolio/self-check.md` | 트러블 7개 git blame 검증 |
| 🟢 | `docs/decisions/llm-provider.md` | OpenAI vs Anthropic 트레이드오프 |
| 🟢 | `docs/decisions/recommendation-algorithm.md` | BE-08 알고리즘 결정 |
| 🟢 | `docs/decisions/youtube-policy.md` | AI-01 YouTube 다운로드 정책 |

24-semester2-plan.md 도 위 7주 통합 패키지 반영해서 재정렬 가능.

---

## 6. 핵심 어필 메시지 (정련 중)

면접·이력서 한 줄 어필 후보. 어느 트랙 선택했는지에 따라 다름.

### 트랙 A — 운영 신뢰성 중심
> "Spring + FastAPI 마이크로서비스 결합 환경에서 Spring 측 신뢰성·관측성을 현업 수준으로 다뤘습니다. Resilience4j 다층 fallback, SLO 기반 운영, 장애 시뮬레이션 + Post-mortem 5건."

### 트랙 B — DB 깊이 중심
> "운동 세션마다 초당 5~10 프레임 × N명 사용자가 만드는 시계열 + JSON 데이터를 파티셔닝·Generated column 인덱싱·아카이빙으로 운영 가능하게 설계. 1억 row 시나리오에서 worst section 조회 p99 X ms 보장."

### 트랙 C — 한 API 깊이 중심 (가장 강함)
> "GET /reports/sessions API를 7번 진화시키며 응답시간 850ms → 12ms (98.6% 감소). RealMySQL 기반 인덱스·MVCC 실험, Redis 캐싱, LLM 비동기 fallback, 분산 락, SLO 정의까지 모든 단계 측정 데이터·트레이드오프 회고 보관."

### 트랙 D — 통합형
> 위 3개 메시지를 하나의 case-study 로 묶음. 가장 시간 들지만 가장 강함.

---

## 7. 다음 액션 후보

우선순위 순:

1. **위 4번 표의 결정 받기** — 특히 분기 H, LLM 제공자, 깊이 트랙 조합 (A'/B'/C'/D')
2. **방학 plan (`19-vacation-plan.md`) 신설** — 시연 핵심 + AWS·Redis + 한 API 깊이 baseline
3. **`docs/portfolio/` 폴더 신설** + 핵심 템플릿 3개:
   - `case-study-{api}.md` (한 API 진화기)
   - `realmysql-experiments.md` (12개 실험 템플릿)
   - `perf-cases.md` (수치 개선 사례 5개)
4. **24-plan 재정렬** — 7주 통합 패키지 반영
5. **트러블 7개 git blame 검증** — 본인 작업 vs 다른 사람 작업 분리

---

## 관련 문서
- [`20-feature-roadmap.md`](./20-feature-roadmap.md) — PPT 요구사항 ↔ 코드 매핑
- [`21-task-assignment.md`](./21-task-assignment.md) — 스택별 작업 분배표
- [`22-backend-tasks-detail.md`](./22-backend-tasks-detail.md) — BE-01~09 상세
- [`23-ai-tasks-detail.md`](./23-ai-tasks-detail.md) — AI-01~03 상세
- [`24-semester2-plan.md`](./24-semester2-plan.md) — 2학기 10주 plan (백엔드 시점)
- [`../architecture/ai-backend-monthly-log.md`](../architecture/ai-backend-monthly-log.md) — 1학기 결합 작업 이력
- [`../decisions/ai-backend-coupling.md`](../decisions/ai-backend-coupling.md) — 결합 결정 이력
