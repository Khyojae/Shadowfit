# Backend 작업 상세 — BE-01 ~ BE-09

마지막 업데이트: 2026-05-24
출처: [`21-task-assignment.md`](./21-task-assignment.md) §2 의 항목별 풀이.
범위: 각 작업이 **어떤 파일을 / 무엇을 / 왜 / 어디까지** 만져야 하는지 + 현재 코드 상태 확인. 담당자가 받아서 바로 시작할 수 있는 단위.

읽는 법:
- **현재 상태**: 코드를 실제로 확인한 결과 (📁 = 존재, ❌ = 부재, ⚠️ = 부분 구현)
- **만질 파일**: 신설/수정 대상 — 파일:라인 표기
- **완료 기준**: 무엇이 되면 끝났다고 볼 수 있는가
- **리스크/의존**: 시작 전에 결정 받아야 할 것

---

## BE-01 — ~~카메라 프레임 프록시 endpoint~~ ✅ 폐기됨

**우선**: ⚪ | **상태**: 🗑️ 폐기 (2026-05-24, 분기 H → H2 채택)

**폐기 사유**: 분기 H 가 H2 (프론트 → AI 직결) 로 확정되어 프록시 endpoint 가 불필요해짐. 자세한 사유는 [`../decisions/ai-backend-coupling.md`](../decisions/ai-backend-coupling.md) §11 결정 로그 2026-05-24 항목.

**대체 작업**: BE-10·11·12 (헬스체크·콜백 검증·Outbox) 가 H2 채택의 결과로 신설됨. 시연 핵심 흐름은 프론트가 AI 직접 호출 + 백엔드는 콜백 수신·세션 관리에 집중.

---

## BE-02 — worst 구간 선정 서비스 로직 보강

**우선**: 🟡 | **추정**: 3h | **의존**: 없음 (지금 시작 가능)

### 현재 상태
- 📁 `WorstSectionDto`: `dto/report/detailreport/WorstSectionDto.java` 존재 — 필드 `exerciseName`, `timeStamp`, `reason` (모두 String, setter 만)
- 📁 `SessionReportResponseDto`: `dto/report/detailreport/SessionReportResponseDto.java` 존재 — 필드 `sessionId`, `avgSyncRate`, `totalReps`, `workoutMinutes`, `caloriesBurned`, `aiSafetyReport`, `worstSection`, `syncRateDetails`, `comparisonWithPrevious`
- ⚠️ `ReportService.java:64-92` — `worstSection`, `syncRateDetails`, `comparisonWithPrevious` 가 null/default 로만 세팅됨 (stub)
- 📁 `PoseData`: `model/exercise/PoseData.java` — `timestampSec`, `syncRate`, `isCorrect`, `feedbackMessage` 컬럼 보유 (worst 구간 선정의 원천 데이터)

### 만질 파일
1. `service/Report/ReportService.java` — `selectWorstSection(sessionId)`, `buildSyncRateDetails(sessionId)`, `buildComparisonWithPrevious(memberId, sessionId)` 3 메서드 신설
   - **worst 선정 알고리즘**: `PoseData` 중 `syncRate` 가장 낮은 timestamp 구간 (또는 연속된 N개의 평균이 가장 낮은 구간)
   - **syncRateDetails**: 시계열 배열 — 프론트가 차트 그릴 수 있는 형태 `[{t: 5.2, sync: 0.78}, ...]`
   - **comparisonWithPrevious**: 직전 동일 운동 세션의 평균 syncRate 와 차이
2. `repository/exercise/PoseDataRepository.java` — `findBySessionIdOrderByTimestampSecAsc(sessionId)` 추가 (이미 비슷한 게 있을 수 있음, 확인 후)

### 완료 기준
- `GET /reports/sessions/{sessionId}` 응답에 worstSection/syncRateDetails/comparisonWithPrevious 가 실데이터로 채워짐
- 단위 테스트 — 합성 PoseData 로 worst 선정 결과 검증

### 리스크/의존
- worst 구간을 "최소 syncRate 한 점" 으로 할지 "연속 구간의 최저 평균" 으로 할지 정의 필요 → 작업자가 1줄 정의하고 시작 (예: "연속된 3 PoseData 의 평균이 가장 낮은 구간")
- "직전 동일 운동" 의 정의 — 같은 `exerciseId`? 같은 카테고리? → 단순 동일 `exerciseId` 권장

---

## BE-03 — GPT/Claude 리포트 자동 생성

**우선**: 🟡 | **추정**: 6h | **의존**: 없음

### 현재 상태
- ❌ `application.yml` / `application.properties` 에 `OPENAI_API_KEY` 또는 `ANTHROPIC_API_KEY` **없음** (작업 분배표의 "이미 env 에 있음" 은 부정확, `.env.example` 만 있을 가능성 — 시작 시 재확인 필요)
- ❌ `GptFeedbackService` 없음
- 📁 `Report` 엔티티: `model/report/Report.java` — `summary`, `improvementTips`, `detailedAnalysis`, `comparisonWithPrevious` 텍스트 필드 보유 (LLM 채울 자리는 준비됨)
- 📁 `ReportService.java:29-56` — 현재는 Report 엔티티 조회/표시만 함. **누가 Report 를 생성하는지 불명확** (현재는 수동 시드 또는 미생성)

### 만질 파일
1. `service/Report/GptFeedbackService.java` 신설
   - 입력: `Session` + 그 세션의 `PoseData` 요약(평균 syncRate, worst 구간, totalReps)
   - 출력: `{summary, improvementTips, detailedAnalysis}` 텍스트 3종
   - 호출: OpenAI Chat Completions 또는 Anthropic Messages API
   - 프롬프트는 한국어 ([`project-korean-only`](../../../C:/Users/khjae/.claude/projects/E--init/memory/project_korean_only.md))
2. `service/Report/ReportService.java` — `generateReportForSession(sessionId)` 추가, `GptFeedbackService` 호출 후 `Report` 엔티티 저장
3. `service/Exercise/ExerciseAnalysisService.java` 또는 gRPC 콜백 `CompleteAnalysis` 처리부 — **세션 종료 콜백 수신 시 비동기로 리포트 생성 트리거** (`@Async` 또는 `ApplicationEventPublisher`)
4. `application.yml` — `openai.api-key: ${OPENAI_API_KEY}` 또는 `anthropic.api-key: ${ANTHROPIC_API_KEY}`
5. `docker-compose.yml` — backend 컨테이너에 env 주입
6. `.env.example` — 키 변수 추가

### 완료 기준
- 운동 세션 종료 → 30초 이내 Report 자동 생성됨
- LLM 호출 실패해도 세션 자체는 정상 종료 (실패 시 Report.status=FAILED 정도, 사용자 화면 영향 없음)
- 단위 테스트 — LLM 응답 mock 으로 Report 저장 확인

### 리스크/의존
- **OpenAI vs Anthropic 선택 필요** — 작업 분배표는 "GPT/Claude" 로 모호. SDK 의존성/단가/응답 품질 트레이드오프 (분기 추가 → `decisions/llm-provider.md` 로 빼는 것 추천)
- LLM 호출은 외부 의존 → 타임아웃·재시도 정책 필요 (5초 timeout, 1회 재시도 정도)
- 응답이 한국어로 안정적으로 나오는 프롬프트 설계 비용

---

## BE-04 — 카테고리 관리 CRUD API

**우선**: 🟢 | **추정**: 3h | **의존**: 없음

### 현재 상태
- 📁 `ExerciseCategory`: `model/exercise/ExerciseCategory.java` — **enum** (LOWER, BACK, UPPER, CORE, FULL). 엔티티가 아니라 enum 인 점에 주의
- 📁 `AdminExerciseController`: `controller/AdminExerciseController.java:19-29` — 현재 `PATCH /admin/exercises/{exerciseId}/thresholds` 1개만 있음
- ❌ 카테고리 추가/삭제 API 없음

### 만질 파일

**선택지 A — enum 유지 (간단, 권장)**
1. `controller/AdminCategoryController.java` 신설
   - `GET /admin/categories` — enum 값 + 그 카테고리에 속한 운동 수
   - 카테고리 자체 CRUD 불가 (enum 이므로 코드 수정 필요)
   - 결과적으로 "카테고리 목록 조회" + "카테고리별 운동 목록" 정도가 끝
2. 이 경우 작업 분량은 1h 로 줄어듦

**선택지 B — 엔티티로 마이그레이션 (본격적 CRUD)**
1. `model/exercise/ExerciseCategory.java` — enum → `@Entity` 로 전환 (코드 광범위 영향)
2. `mysql/schema.sql` — `exercise_categories` 테이블 신설, `exercises.category_id` FK
3. `controller/AdminCategoryController.java` 신설 — `POST/PATCH/DELETE /admin/categories`
4. `repository/exercise/ExerciseCategoryRepository.java` 신설
5. 기존 `Exercise.category` 참조하는 모든 코드 수정 (Exercise.java, DTO, 서비스 다수)

### 완료 기준
- A: `GET /admin/categories` 가 enum 값 + 운동 개수 반환
- B: 관리자가 새 카테고리 추가/삭제 가능

### 리스크/의존
- **enum 유지 vs 엔티티 전환 선택이 결정 필요** — 현재 enum 인데 시연용으론 A 로 충분. 운영 단계에서 카테고리를 비개발자가 추가해야 하면 B 필요
- B 선택 시 추정 3h → 6h+ 로 증가 (영향 범위가 큼)
- 시작 전 사용자 확인 권장 ([`feedback-preview-scope-before-bulk-changes`](../../../C:/Users/khjae/.claude/projects/E--init/memory/feedback_preview_scope_before_bulk_changes.md))

---

## BE-05 — 관리자 대시보드 통계 API

**우선**: 🟢 | **추정**: 4h | **의존**: 없음

### 현재 상태
- ❌ 관리자 통계 endpoint 없음
- 📁 집계 대상 도메인: `Member` (`model/member/Member.java`), `Session` (`model/exercise/Session.java`), `Report` (`model/report/Report.java`)

### 만질 파일
1. `controller/AdminDashboardController.java` 신설 (또는 `AdminExerciseController` 에 추가)
   - `GET /admin/stats/overview` — 가입자 수, 활성 사용자(최근 7일), 총 세션 수, 평균 syncRate
   - `GET /admin/stats/sessions?from=...&to=...` — 일별 세션 수 시계열
   - `GET /admin/stats/exercises` — 운동별 인기도 (세션 수 기준 top)
2. `service/admin/AdminStatsService.java` 신설
3. `repository/exercise/SessionRepository.java` — count/group-by 메서드 추가 (`countByCreatedAtBetween`, `groupByExerciseId` 등)
4. `dto/admin/stats/OverviewResponseDto.java`, `SessionsTimeseriesResponseDto.java`, `ExercisePopularityResponseDto.java`

### 완료 기준
- 3 endpoint 모두 응답
- 관리자 권한 검증 (`@PreAuthorize("hasRole('ADMIN')")` 또는 SecurityConfig 에서)
- 기존 시드 데이터로 응답이 0/null 이 아닌 의미 있는 숫자 나옴

### 리스크/의존
- **Member 엔티티에 role/admin 필드 존재 여부** 확인 필요 (현재 Security 가 어떻게 관리자 구분하는지 확인 후 시작)
- 집계 쿼리가 무거우면 캐싱 필요 (시연용은 일단 무캐시)

---

## BE-06 — 운동 목표 엔티티·CRUD API

**우선**: 🟢 | **추정**: 5h | **의존**: 없음

### 현재 상태
- ❌ `Goal` 엔티티 **없음**
- ❌ Member 에 `weeklyGoal`/`target` 필드 **없음**
- 📁 비교용: `Onboarding` 흐름에 사용자 목적 입력 받는 부분이 있는지는 별도 확인 필요

### 만질 파일
1. `model/member/Goal.java` 신설
   - 필드: `id`, `member` (FK), `goalType` (enum: WEEKLY_SESSIONS, WEEKLY_MINUTES, TARGET_WEIGHT 등), `targetValue`, `currentValue`, `periodStart`, `periodEnd`, `status` (IN_PROGRESS/ACHIEVED/FAILED)
2. `repository/member/GoalRepository.java` 신설
3. `controller/GoalController.java` 신설
   - `POST /goals` — 목표 설정
   - `GET /goals` — 본인 목표 조회
   - `PATCH /goals/{id}` — 목표 수정
   - `DELETE /goals/{id}` — 삭제
4. `service/Goal/GoalService.java` 신설
   - **목표 진척도 자동 갱신**: 세션 종료 콜백 시점에 `currentValue` += 세션 분/횟수 (이벤트 리스너 또는 `CompleteAnalysis` 콜백 핸들러에서 호출)
5. `dto/goal/*` — Request/Response DTO 4종
6. `mysql/schema.sql` — `goals` 테이블

### 완료 기준
- CRUD 4 endpoint 모두 동작
- 세션 종료 시 관련 목표의 `currentValue` 자동 증가
- 단위 테스트 — 주간 세션 목표 진척 시나리오

### 리스크/의존
- **목표 종류(goalType) 정의**가 비개발자 합의 필요 — 시연용은 WEEKLY_SESSIONS 한 종만 먼저 구현 권장
- 진척 자동 갱신 트리거 지점을 [`136f0e6 SessionTimeoutScheduler`](../architecture/ai-backend-monthly-log.md) 흐름과 동일한 곳에 두면 자연스러움

---

## BE-07 — 사용자 운동 패턴 분석 API

**우선**: 🟢 | **추정**: 8h+ | **의존**: 데이터 축적 후

### 현재 상태
- ❌ 패턴 분석 endpoint 없음
- 📁 `SessionService.getWeeklyActivity()`, `getCalendarMain()` 존재 — **단순 조회/집계** 수준 (패턴 추론 아님)

### 만질 파일
1. `controller/PatternAnalysisController.java` 신설
   - `GET /patterns/periodicity` — 사용자가 주로 운동하는 요일/시간대
   - `GET /patterns/intensity-trend` — 4주간 평균 syncRate·총 분 추세
   - `GET /patterns/consistency` — 연속 운동일 수, 빠진 날 수
2. `service/Analysis/PatternAnalysisService.java` 신설
3. `repository/exercise/SessionRepository.java` — 시계열 집계 메서드 추가

### 완료 기준
- 3 endpoint 응답
- 최소 4주치 데이터가 있는 사용자에서 의미 있는 결과 나옴

### 리스크/의존
- **사용자당 최소 4주(28세션) 데이터가 있어야 의미 있음** → 시연 초기에는 mock/stub 응답 권장
- 알고리즘 정의가 모호 — 분기로 빼서 결정 필요: 단순 통계? 클러스터링? → 시연용은 단순 통계 (요일별 평균, 시간대별 분포)

---

## BE-08 — 개인화 루틴 추천 API

**우선**: 🟢 | **추정**: 10h+ | **의존**: BE-07

### 현재 상태
- ❌ 추천 endpoint 없음
- ❌ 추천 알고리즘 설계 안 됨

### 만질 파일
1. `controller/RecommendationController.java` 신설
   - `GET /recommendations/today` — 오늘 추천 루틴 (3개 운동)
   - `GET /recommendations/weekly` — 1주 단위 추천 플랜
2. `service/Recommendation/RoutineRecommendationService.java` 신설
3. `dto/recommendation/*`

### 완료 기준
- 추천 결과 응답 + 추천 근거 한 줄 (`"최근 하체 운동이 적어서"`)

### 리스크/의존
- **알고리즘 자체가 설계 안 됨** — 작업 시작 전 `decisions/recommendation-algorithm.md` 신설 필수
  - 후보: 규칙 기반(가장 약한 카테고리 보강) / 협업 필터링 / GPT 호출
  - 시연용은 **규칙 기반 1안** 추천 (가장 가벼움, 1~2h)
- BE-07 의 패턴 분석 결과를 입력으로 사용

---

## BE-09 — 운동 세트 개념 도입 (보류)

**우선**: ⚪ | **추정**: 5h | **상태**: 🟦 보류 | **의존**: 새 운동 추가 시점

### 현재 상태
- 📁 `Session.java:43` — `totalReps` 만 있고 `setCount`/`setIndex` **없음**
- 📁 `PoseData.java` — set 필드 **없음**
- 📁 `ai-server/app/proto/exercise.proto:94-99` (`PoseDataRequest`) — `timestamp_sec`, `joint_coordinates`, `sync_rate`, `feedback_message`. set 필드 **없음**

### 만질 파일 (보류 해제 시)
1. **proto** (양쪽 동기 필요)
   - `backend/src/main/proto/exercise.proto` — `PoseDataRequest` 에 `set_index(5)`, `rep_index_in_set(6)` 추가
   - `ai-server/app/proto/exercise.proto` 동일 변경
2. `model/exercise/Session.java` — `setCount`, `restSeconds` 컬럼 추가
3. `model/exercise/PoseData.java` — `setIndex` 컬럼 추가
4. `mysql/schema.sql` — 컬럼 추가 (또는 Flyway 마이그레이션, OP-04 의존)
5. `dto/exercises/session/*` — 세트 정보 노출
6. `service/Exercise/PoseDataService.java`, `ExerciseGrpcService.java` — 새 필드 처리

### 완료 기준
- 스쿼트 외 운동 추가 + 세트 단위 분석 결과 저장/조회 가능

### 리스크/의존
- [`project-squat-first`](../../../C:/Users/khjae/.claude/projects/E--init/memory/project_squat_first.md) 에 의해 **현재 스쿼트만 다룸 → 세트 개념 자체가 필요 없음**. 새 운동 추가 결정 전까지 보류
- AI 측 분석기가 세트 구분을 어떻게 할지 (AI-03 와 협의 필요) → [`feedback-minimize-python-changes`](../../../C:/Users/khjae/.claude/projects/E--init/memory/feedback_minimize_python_changes.md) 정책상 AI 원작자와 합의 필요

---

## BE-10 — AI gRPC 헬스체크 + Resilience4j Circuit Breaker (H2 채택 부속)

**우선**: 🔴 | **추정**: 4h | **의존**: 분기 H = H2 확정

### 현재 상태
- ❌ AI 서버 헬스체크 없음 — Spring 이 AI 다운 여부 모름
- ❌ Resilience4j 미도입
- 📁 `service/Exercise/ExerciseAnalysisService.java` — gRPC 호출 후 실패 시 단순 throw

### 만질 파일
1. `backend/build.gradle` — `resilience4j-spring-boot3`, `resilience4j-reactor` 추가
2. `service/Exercise/AiHealthCheckService.java` 신설 (+50줄)
   - gRPC `grpc.health.v1.Health/Check` 주기 호출 (10초 fixedDelay)
   - 결과를 `AtomicReference<AiServerStatus>` 에 저장
3. `service/Exercise/ExerciseAnalysisService.java` ±15
   - `startAnalysis` 진입 시 `AiHealthCheckService.isUp()` 확인 → DOWN 이면 `AiUnavailableException`
4. `global/grpc/GrpcCircuitBreakerInterceptor.java` 신설 — gRPC client interceptor 로 모든 호출에 Circuit Breaker 적용 (`failureRateThreshold: 50%`, `minimumNumberOfCalls: 5`, `waitDurationInOpenState: 30s`)
5. `application.yml` — `resilience4j.circuitbreaker.instances.aiServer` 설정

### 완료 기준
- AI 서버 정지 → 30초 안에 헬스체크가 DOWN 감지
- 운동 시작 요청 시 503 (AI 점검 중) 응답
- Circuit Breaker open 후 30초 뒤 half-open 진입
- 테스트: AI 컨테이너 stop → 새 세션 요청 거부 검증

### 리스크/의존
- 헬스체크 주기 (10초) 가 짧으면 부하, 길면 감지 지연 → SLO 와 맞춰 결정
- Circuit Breaker `failureRateThreshold` 디폴트 50% 가 우리 SLO 와 맞는지 측정 필요 (RealMySQL 적용 실험과 같은 패턴: AI 추천 → 측정 → 조정)

---

## BE-11 — 콜백 PoseData 검증 게이트 (H2 채택 부속)

**우선**: 🔴 | **추정**: 3h | **의존**: 분기 H = H2 확정

### 현재 상태
- ⚠️ `ExerciseGrpcService.SavePoseDataBatch` (`service/Exercise/ExerciseGrpcService.java`) — AI 콜백을 거의 그대로 저장
- ❌ sessionId 소유권 / memberId 일치 / 시간 범위 / syncRate 범위 검증 없음
- 📁 H2 채택으로 프론트가 AI 직접 호출 → 임의 sessionId 로 호출 가능 → AI 가 그 sessionId 로 콜백 → 백엔드가 무방비로 받음

### 만질 파일
1. `service/Exercise/PoseDataValidationGate.java` 신설 (+80줄)
   - `validate(sessionId, batch)` — 다음 검증:
     - sessionId 유효 + status IN_PROGRESS
     - memberId 일치 (세션 소유자 vs 콜백 시점)
     - 각 PoseData 의 `timestamp_sec` 가 세션 시작 시간 + 합리적 범위 (0 ~ 60분) 안
     - `sync_rate` 가 [0.0, 1.0] 범위 안
     - 배치 크기 한도 (1000건 등)
   - 무효 시 `InvalidPoseDataException` + 메트릭 카운트
2. `service/Exercise/PoseDataService.java` ±10 — `savePoseDataBatch` 호출 전 검증 게이트 통과
3. `service/Exercise/ExerciseGrpcService.java` ±5 — 예외 처리, gRPC `INVALID_ARGUMENT` 응답
4. Micrometer 메트릭: `posedata.callback.invalid.total{reason}` (sessionId / range / size / member)

### 완료 기준
- 무효 sessionId 콜백 → 거부 + 메트릭 카운트
- 정상 콜백 → 저장
- 단위 테스트 5개 (각 검증 항목별)

### 리스크/의존
- 검증 비용 — 매 콜백마다 sessionId DB 조회 추가 (캐시 권장: Caffeine 1분 TTL)
- 무효 콜백이 정상 운영 중 발생 가능성 — AI 가 잘못 보낼 케이스도 있어서 로그·알람 정책 동시 설계

---

## BE-12 — 콜백 처리 Outbox 패턴 (H2 채택 부속, 선택)

**우선**: 🟡 | **추정**: 5h | **의존**: BE-11

### 현재 상태
- ⚠️ AI 콜백 → 백엔드 트랜잭션 내 PoseData 저장 + 통계 갱신 + Goal 진척 갱신 — **트랜잭션 길어지면 락 경합**, 일부 실패 시 무엇이 적용되고 안 됐는지 불분명
- ❌ Outbox 테이블 없음

### 만질 파일
1. `mysql/schema.sql` (Flyway) — `pose_data_outbox` 테이블 신설 (id, session_id, payload JSON, status, retry_count, created_at, processed_at)
2. `service/Exercise/PoseDataIngestService.java` 신설 (+120줄)
   - `ExerciseGrpcService` 의 콜백 진입점이 호출
   - 1단계 (콜백 트랜잭션): Outbox 에만 INSERT, 즉시 ACK
   - 2단계 (별도 트랜잭션, `@Scheduled` 또는 `@Async`): Outbox 에서 꺼내 PoseData 저장 + 통계 갱신 + Goal 진척
   - 실패 시 retry_count++, max 3회 → DLQ (status=FAILED)
3. `service/Exercise/ExerciseGrpcService.java` ±15
4. `service/admin/OutboxDlqController.java` (선택) — DLQ 조회·재처리

### 완료 기준
- 콜백 응답이 100ms 안에 ACK (Outbox INSERT 만)
- 다운스트림 실패 시 재시도 3회 후 DLQ
- 단위 테스트: 실패 케이스 + DLQ 이동 검증

### 리스크/의존
- 복잡도 ↑ — 트랜잭션 분리로 일관성 약화 (eventual consistency). 사용자가 콜백 후 즉시 리포트 조회 시 데이터 없을 수 있음 → 프론트가 폴링/재시도
- BE-12 는 시연 필수 X, 운영 단계에서 가치 큼. 방학 후반 또는 2학기 작업으로

---

## BE-13 — TTS 피드백 템플릿 페르소나 분기 적용 + BT-SET 송신 trigger 지원

**우선**: 🟡 | **추정**: 3h (페르소나 2.5h + BT-SET 0.5h) | **의존**: 없음 (지금 시작 가능) | **상태**: 📋

배경: [`../decisions/tts-design.md`](../decisions/tts-design.md) §11.3 갱신 11 + §2.A.BT 갱신 13, [`../handoff/tts-negotiation-checklist.md`](../handoff/tts-negotiation-checklist.md) #1·3·10·12·13·24. 기존 TTS 도메인(커밋 `2f48526`)은 페르소나 무관 단일 멘트만 제공 + 세션 종료 1회 batch 만 지원. 분기 4-A + 페르소나 시스템([`../12-persona-difficulty.md`](../12-persona-difficulty.md)) + BT-SET (세트 경계 batch + 멱등성) 결합.

### 현재 상태
- ✅ `Member.selectedPersona` (enum BEGINNER/ADVANCED/DIET/REHAB, default BEGINNER) — `model/member/Member.java:36`
- ✅ `ExerciseFeedbackTemplate` 엔티티 + `(exercise_id, feedback_type)` uniqueKey — `model/exercise/ExerciseFeedbackTemplate.java`
- ✅ `FeedbackTemplateController.getTemplates(exerciseId)` — `controller/FeedbackTemplateController.java`
- ✅ seed 데이터 (스쿼트 4건 등) — `mysql/data.sql:130-147` (모두 페르소나 무관 단일 멘트)
- ❌ `ExerciseFeedbackTemplate.persona` 컬럼 없음
- ❌ 페르소나별 16 row seed 없음
- ❌ Controller 의 페르소나 자동 필터링 없음

### 만질 파일

**A. 페르소나 분기 (2.5h)**
1. `mysql/schema.sql:120-128` + `mysql/data.sql:79-87` — `exercise_feedback_templates` 테이블에 `persona VARCHAR(10) NULL` 컬럼 추가, uniqueKey 를 `(exercise_id, feedback_type, persona)` 로 변경. `persona IS NULL` row 는 *공통 fallback* 의미
2. `model/exercise/ExerciseFeedbackTemplate.java` — `@Enumerated SelectedPersona persona` 필드 + uniqueConstraint 갱신 (~10줄)
3. `repository/exercise/ExerciseFeedbackTemplateRepository.java` — `findByExerciseAndPersonaWithFallback(exercise, persona)` 신설. 페르소나 row 가 있으면 그것, 없으면 `persona IS NULL` fallback 반환 (~15줄)
4. `service/Exercise/FeedbackTemplateService.java` — 토큰의 사용자 → `selectedPersona` 조회 → repo 호출 (~10줄)
5. `controller/FeedbackTemplateController.java` — `@AuthenticationPrincipal` 추가, service 위임 (~5줄)
6. `mysql/data.sql` — 스쿼트 4 결함 × 4 페르소나 = 16 row seed (스쿼트만 우선, 런지·플랭크는 후속) (~20줄)
   - 12-persona-difficulty.md 의 페르소나별 톤 가이드 활용

**B. BT-SET 송신 trigger 지원 (0.5h)** — AI 가 *세트 경계마다* batch 송신할 수 있게 endpoint 확장. 협의 안건 #3 (snake_case + set_no/is_final) 및 #10 (멱등성) 동시 처리.

> **✅ 2026-05-26 갱신**: 채널을 REST → gRPC 로 통일. 작업 7·8 (DTO snake_case) 무효화 — DTO 폐기되고 proto 가 schema 강제. 작업 10·11 도 proto 직접 수신 형태로 재구성. 박제: [`../decisions/tts-design.md`](../decisions/tts-design.md) 상단 박스.

7. ~~`dto/exercises/feedback/FeedbackBatchRequestDto.java` — `set_no`, `is_final` 필드 추가 + `@JsonNaming`~~ **→ 2026-05-26 DTO 자체 삭제됨**. proto `FeedbackBatchRequest` 가 대체. 필드는 proto 에 동일하게 `int32 set_no = 2; bool is_final = 3;` 으로 존재
8. ~~`dto/exercises/feedback/FeedbackEventDto.java` — `@JsonNaming`~~ **→ 2026-05-26 DTO 자체 삭제됨**. proto `FeedbackEvent` 가 대체. snake_case 자동 (proto 기본)
9. `model/exercise/SessionFeedbackLog.java` + `mysql/schema.sql:132-140` — uniqueKey 추가 `(session_id, occurred_at, feedback_type)`. 멱등성 (협의 안건 #10) 보장
   ```java
   @Table(name = "session_feedback_logs",
          uniqueConstraints = @UniqueConstraint(
              name = "uk_session_event",
              columnNames = {"session_id", "occurred_at", "feedback_type"}),
          indexes = @Index(name = "idx_session_feedback", columnList = "session_id, occurred_at"))
   ```
10. `service/Exercise/FeedbackLogService.java` — `saveBatch(FeedbackBatchRequest proto)` 시그니처 (D-2 채택). native `INSERT IGNORE` 사용. AI 측 retry 가 같은 events 재송신해도 안전 흡수
11. `service/Exercise/ExerciseGrpcService.java` — `reportFeedbackBatch` 핸들러 신규. proto → `FeedbackLogService.saveBatch` 위임. 응답에 `saved_count` 포함. ~~`InternalFeedbackController` REST endpoint~~ → 삭제됨

### 완료 기준

**페르소나 분기**:
- `GET /exercises/1/feedback-templates` 호출 시 토큰의 사용자 페르소나(예: HEALCHANG) 에 맞는 4건 반환
- 페르소나 변경(`PATCH /users/me/persona`) 후 다음 호출 시 새 페르소나 멘트 반환
- 페르소나 row 가 없는 운동은 `persona IS NULL` fallback 반환 (런지·플랭크 — 후속 작업까지 호환)
- 단위 테스트 — 4 페르소나 × 1 호출 = 4 응답 검증

**BT-SET 지원**:
- gRPC `ExerciseService.ReportFeedbackBatch` 가 `FeedbackBatchRequest{session_id, set_no, is_final, events:[...]}` proto 수신 (snake_case 는 proto 기본)
- 같은 세션의 다중 batch 정상 처리 (각각 별 row 로 적재)
- 같은 `(session_id, occurred_at, feedback_type)` 의 재송신 시 멱등 (INSERT IGNORE 동작, 중복 row 생성 안 됨)
- 단위 테스트 — 같은 events 2번 송신 → DB row 수 1번과 동일

### 리스크/의존

협의 안건 — 상세는 [`../handoff/tts-negotiation-checklist.md`](../handoff/tts-negotiation-checklist.md) 의 "BE 작업별 협의 매핑 → BE-13" 및 "각 안건 상세 설명" 참조.

| 안건 | 누구와 | 시점 | 차단? |
|---|:-:|:-:|:-:|
| **#1 8종 enum 표기 master** — `FeedbackType.java` ↔ `REQUIREMENTS.md` §6 정합 | 3자 | 작업 전 | 🔴 |
| **#2 페르소나 enum 표기 master** — `SelectedPersona` ↔ `12-persona-difficulty.md` 정합 | 3자 | 작업 전 | 🔴 |
| **#3 batch payload schema** — snake_case + set_no/is_final + `@JsonNaming` (BT-SET 결과) | AI | 작업 전 | 🔴 |
| **#10 재시도·멱등성** — `(session_id, occurred_at, feedback_type)` uniqueKey + INSERT IGNORE | AI | 작업 중 | 🟡 |
| **#12 응답 구조** — Map vs Array (Array 권장) | Front | 작업 중 | 🟡 |
| **#13 캐시 무효화** — `PATCH /users/me/persona` 후 클라 reload 신호 | Front | 작업 중 | 🟡 |
| **#24 빈 결과 처리** — `persona IS NULL` fallback (repo 의 fallback 쿼리 정합) | Front | 작업 중 | 🟡 |
| **#25 priority 응답 메타** — 클라 미사용 → 제외 권장 | Front | 작업 중 | 🟢 |
| **#28 enum 추가 시 배포 순서** — Spring → AI → Front | 3자 | 운영 단계 | 🟢 |

기타:
- seed 데이터의 멘트 문구 — `12-persona-difficulty.md` 의 톤 가이드 참고하되 *짧은 명령형* 으로 통일 ([`../decisions/tts-design.md`](../decisions/tts-design.md) 분기 8 가치 3 "인지" 정합)
- 기존 `data.sql:130-134` 의 스쿼트 4건이 *페르소나 무관 단일 멘트* 라 — 새 schema 에서는 `persona IS NULL` 로 INSERT 하여 fallback row 로 보존하거나, 4 페르소나 row 로 분할 후 폐기

---

## BE-14 — Session 종료 endpoint (분기 2.A.ET ET-H, 재검토 2026-05-26)

**우선**: 🔴 | **추정**: 1.5h | **의존**: 없음 | **상태**: 📋

배경: [`../decisions/tts-design.md`](../decisions/tts-design.md) 분기 2.A.ET. 클라가 "운동 종료" 버튼 누르면 *Spring 에 종료 시각 기록* + *AI 에 batch 송신 trigger* 양쪽에 통보. Spring 측은 `endTime` 갱신만 담당. AI batch 송신은 AI 가 별도 수신·처리 (handoff `ai-tts-feedback-batch.md` §2.F).

### 현재 상태
- ✅ `Session.endTime` 컬럼 존재 — `model/exercise/Session.java:40`
- ✅ `PATCH /sessions/{id}/end` endpoint 구현 완료 — `SessionController.endSession`
- ✅ ET-H 채택 (2026-05-26 재검토): Spring 이 endTime 기록 + afterCommit gRPC `StopAnalysis` 호출. 클라는 단일 endpoint 만 호출 (Spring 이 분배자). 분석: [`../decisions/session-end-trigger.md`](../decisions/session-end-trigger.md)

### 만질 파일 (완료됨)
1. ✅ `controller/SessionController.java` — `PATCH /sessions/{id}/end` 구현
2. ✅ `service/Exercise/SessionService.java` — `endSession(sessionId, member)`: 권한 검증 + endTime 기록 + `TransactionSynchronization.afterCommit` 안에서 `analysisService.stopAnalysis(sessionId)` 호출
3. ~~`dto/session/SessionEndDto.java`~~ — body 불필요 (서버 시각 권위 채택)

### 완료 기준
- `PATCH /sessions/{id}/end` 호출 시 `Session.endTime` 갱신
- 본인 session 아니면 403
- 이미 종료된 session 재호출 시 멱등 (200 OK, 변경 없음)

### 리스크/의존

협의 안건 — 상세는 [`../handoff/tts-negotiation-checklist.md`](../handoff/tts-negotiation-checklist.md) 의 "BE 작업별 협의 매핑 → BE-14" 및 "각 안건 상세 설명" 참조.

| 안건 | 누구와 | 시점 | 차단? |
|---|:-:|:-:|:-:|
| ~~**#5 인증·토큰 endpoint 분리**~~ | — | 해소 | ✅ 2026-05-26 gRPC 통일로 `/internal/*` REST 자체 소멸 |
| ~~**#7 클라 양방향 호출 순서**~~ | — | 해소 | ✅ ET-H 채택으로 클라는 1 endpoint 만 호출 |
| ~~**#6 (AI 측) 종료 신호 형식**~~ | — | 해소 | ✅ Spring 이 afterCommit gRPC `StopAnalysis` 송신, AI 는 기존 핸들러 그대로 |
| **#16 시간대 형식** — Asia/Seoul 단일 TZ. `endTime` 서버 시각 권위 | 3자 | 작업 중 | 🟢 박힘 (`TZ=Asia/Seoul`) |

기타:
- 통계 갱신 (`avgSyncRate`, `totalReps`) — endpoint 는 *시각만 기록*. 통계는 AI 의 `CompleteAnalysis` 콜백이 별도 갱신
- 이미 종료된 session 재호출 시 멱등 (200 OK, `endTime` 미변경, AI gRPC 도 안 부름)

---

## BE-15 — 세션 피드백 조회 API

**우선**: 🟡 | **추정**: 2.5h | **의존**: 없음 (기존 `SessionFeedbackLog` 활용) | **상태**: 📋

배경: [`../decisions/tts-design.md`](../decisions/tts-design.md) §11.1.C. 리포트 화면에서 세션의 결함 패턴 조회. BE-30 (LLM 효과 분석) 의 데이터 소스이기도 함.

### 현재 상태
- ✅ `SessionFeedbackLog` 엔티티 + 인덱스 `(session_id, occurred_at)` — `model/exercise/SessionFeedbackLog.java`
- ✅ AI 가 세션 종료 시 batch 송신 → `FeedbackLogService.saveBatch` 로 적재 (커밋 `2f48526`)
- ❌ 조회 API 없음 — `GET /sessions/{id}/feedbacks`, `feedback-summary` 둘 다 미구현

### 만질 파일
1. `controller/SessionFeedbackController.java` 신설 (~40줄)
   - `GET /sessions/{id}/feedbacks` — events 리스트, 페이징 옵션
   - `GET /sessions/{id}/feedback-summary` — feedback_type 별 카운트 + sync_rate 통계
2. `service/Exercise/SessionFeedbackQueryService.java` 신설 (~30줄)
3. `repository/exercise/SessionFeedbackLogRepository.java` — `findBySessionIdOrderByOccurredAtAsc`, 집계 쿼리 (~10줄)
4. `dto/session/SessionFeedbackResponseDto.java`, `SessionFeedbackSummaryDto.java` (~20줄)

### 완료 기준
- `GET /sessions/{id}/feedbacks` → 결함 이벤트 리스트 반환 (occurred_at 오름차순)
- `GET /sessions/{id}/feedback-summary` → 결함별 카운트 + 평균/최소/최대 sync_rate
- 본인·트레이너 권한 검증
- 단위 테스트 — 합성 SessionFeedbackLog 로 집계 결과 검증

### 리스크/의존

협의 안건 — 상세는 [`../handoff/tts-negotiation-checklist.md`](../handoff/tts-negotiation-checklist.md) 의 "BE 작업별 협의 매핑 → BE-15" 및 "각 안건 상세 설명" 참조.

| 안건 | 누구와 | 시점 | 차단? |
|---|:-:|:-:|:-:|
| **#1 8종 enum 표기** — 응답에 `feedbackType` 포함 (BE-13 과 동시 미팅에서 일괄) | 3자 | 작업 전 | 🔴 |
| **#16 시간대 형식** — `occurredAt` 응답 표기 (BE-14 와 동시) | 3자 | 작업 전 | 🔴 |
| **#17 summary 집계 단위** — `feedback_type` 별 카운트 + sync_rate avg/min/max 권장 | Front | 작업 중 | 🟡 |
| **#19 트레이너 권한 헤더** — 기존 권한 모듈 (JWT role 클레임) 재사용 | S 단독 | 작업 중 | 🟡 |
| **#18 events 페이징** — rep 30 × 결함 7 ≈ 210건 가능. *MVP 는 단순 list*, 운영 시 페이징 도입 | Front | 차순위 | 🟢 |

기타:
- 결함 수 많을 때 (rep 30 × 결함 7 ≈ 210건) 페이징 필요할 수 있음 — MVP 는 단순 list 후 운영 시 페이징 도입

---

## BE-30 — TTS 피드백 효과 분석 (선택, 포폴 어필용)

**우선**: 🟢 | **추정**: 4h | **의존**: BE-07 (패턴 분석) 와 묶기 권장 | **상태**: 📋

### 현재 상태
- 📁 TTS 도메인 자체는 완성 (2f48526, 2026-05-09) — `PreferenceController`, `FeedbackTemplateController`, ~~`InternalFeedbackController`~~ (2026-05-26 삭제), `SessionFeedbackLog`, `PreferenceService` 등 7개 파일
- ✅ FastAPI 가 세트 경계·세션 종료 시 gRPC `ReportFeedbackBatch` 송신 → `SessionFeedbackLog` 저장 (2026-05-26 gRPC 통일)
- ❌ 저장된 발화 이벤트가 분석되지 않음 — 단순 로그만

### 만질 파일
1. `service/Analysis/FeedbackEffectAnalysisService.java` 신설
   - 사용자별: TTS 활성화 vs 비활성화 그룹 syncRate 평균 비교
   - 운동별: 특정 피드백 멘트 (`feedbackType`) 가 자주 발화된 세션 vs 안 발화된 세션의 다음 세션 syncRate 변화
   - 멘트별: 발화 횟수 → 다음 rep syncRate 개선도 상관관계
2. `controller/admin/AdminAnalyticsController.java` 또는 BE-05 의 `AdminDashboardController` 에 추가
   - `GET /admin/analytics/feedback-effect` — 위 3가지 지표 응답
3. `repository/exercise/SessionFeedbackLogRepository.java` — 집계 쿼리 메서드 추가

### 완료 기준
- 관리자 endpoint 1개로 3가지 지표 응답
- 4주치 데이터로 의미 있는 결과 (TTS 그룹 vs 비TTS 그룹 syncRate 차이 ≥ 1% 정도면 어필 가능)

### 리스크/의존
- **데이터 축적 4주 이상 필요** — BE-07 와 같은 시점
- 통계적 유의성 약함 (베타 사용자 5~10명) — 면접에서 "샘플 크기 의식했다" 고 답할 수 있어야 함
- TTS 그룹과 비TTS 그룹의 사용자 특성 차이 (confounding) — 단순 A/B 가 아님을 의식

### 포폴 어필 가치
- "데이터 기반 기능 효과 검증" — 신입에 드문 시그널
- BE-03 (LLM 리포트) 도입 후엔 LLM 리포트 효과 측정으로 확장 가능 (같은 패턴)
- 면접 답변: "내가 만든 기능이 사용자에게 효과 있는지 측정해봤다 — 가설/방법/결과/한계" 4단 회고

---

## 권장 시작 순서

**1주차 (시연 직결, H2 채택의 결과)**:
1. **BE-10** (AI 헬스체크 + Circuit Breaker — AI 다운 시 사용자 경험 보호)
2. **BE-11** (콜백 검증 게이트 — H2 보안 보강 핵심)

**1~2주차 (시연 풍성하게)**:
3. **BE-02** (worst 구간) — 다른 작업과 독립
4. **BE-03** (GPT 리포트) — LLM 제공자 결정 선행

**2주차 이후 (관리/장기)**:
5. **BE-04** (카테고리 — A안 1h 로 가볍게)
6. **BE-05** (관리자 통계)
7. **BE-06** (Goal)
8. **BE-12** (Outbox 패턴 — 운영 단계 신뢰성)
9. **BE-07** → **BE-08** (패턴 → 추천, 데이터 충분해진 후)
10. **BE-30** (TTS 피드백 효과 분석 — BE-07 와 같은 시점에 묶기)

**폐기·보류**:
- ~~BE-01~~ 🗑️ 폐기 (H2 채택)
- **BE-09** — 새 운동 추가 결정 시점에 해제

---

## 작업 시작 전 결정 받아야 할 항목

| 작업 | 결정 사항 | 상태 |
|------|---------|------|
| ~~BE-01~~ | ~~분기 H~~ | ✅ 결정됨 — H2 (2026-05-24) |
| BE-10·11 | 분기 I 인증 토큰 흐름 (a 정적 / b 단기 / c JWT) | ✅ 잠정 I1 (2026-05-24), 운영 단계에서 재검토 |
| BE-03 | LLM 제공자 (OpenAI vs Anthropic) | `decisions/llm-provider.md` 신설 후 결정 |
| BE-04 | 카테고리 enum 유지 vs 엔티티 전환 | A (enum 유지) → 1h |
| BE-07 | 패턴 분석 알고리즘 | 단순 통계 (요일별/시간대별) |
| BE-08 | 추천 알고리즘 | `decisions/recommendation-algorithm.md` 신설 후, 규칙 기반 권장 |

---

## 최종 신설 API 목록 (전체 합치면)

BE-01~08 가 모두 완료되면 새로 생기는 endpoint 전체. 인증 칸: 👤 = 본인 JWT, 🛡️ = 관리자, 🔁 = 신설 아니라 응답 필드만 채워짐.

### 사용자용 API

| 작업 | Method | Endpoint | 응답 요지 | 인증 |
|------|--------|----------|---------|------|
| ~~BE-01~~ | ~~POST~~ | ~~`/exercises/sessions/{sessionId}/frame`~~ | 🗑️ 폐기 (H2 채택) — 프론트가 AI `POST /pose` 직접 호출 | — |
| BE-02 | GET | `/reports/sessions/{sessionId}` 🔁 | 기존 응답에 worstSection / syncRateDetails / comparisonWithPrevious 채워짐 (새 endpoint 아님) | 👤 |
| BE-06 | POST | `/goals` | 목표 생성 (goalType, targetValue, periodStart/End) | 👤 |
| BE-06 | GET | `/goals` | 본인 목표 목록 (currentValue 자동 갱신됨) | 👤 |
| BE-06 | PATCH | `/goals/{goalId}` | 목표 수정 | 👤 |
| BE-06 | DELETE | `/goals/{goalId}` | 목표 삭제 | 👤 |
| BE-07 | GET | `/patterns/periodicity` | 주로 운동하는 요일/시간대 | 👤 |
| BE-07 | GET | `/patterns/intensity-trend` | 4주간 평균 syncRate · 총 분 추세 | 👤 |
| BE-07 | GET | `/patterns/consistency` | 연속 운동일 수, 빠진 날 수 | 👤 |
| BE-08 | GET | `/recommendations/today` | 오늘 추천 루틴 (3개 운동) + 근거 한 줄 | 👤 |
| BE-08 | GET | `/recommendations/weekly` | 1주 단위 추천 플랜 | 👤 |

### 관리자용 API

| 작업 | Method | Endpoint | 응답 요지 | 인증 |
|------|--------|----------|---------|------|
| BE-04(A) | GET | `/admin/categories` | enum 값 + 카테고리별 운동 수 | 🛡️ |
| BE-04(B) | POST | `/admin/categories` | 카테고리 생성 (B안 채택 시만) | 🛡️ |
| BE-04(B) | PATCH | `/admin/categories/{id}` | 카테고리 수정 (B안 채택 시만) | 🛡️ |
| BE-04(B) | DELETE | `/admin/categories/{id}` | 카테고리 삭제 (B안 채택 시만) | 🛡️ |
| BE-05 | GET | `/admin/stats/overview` | 가입자 수, 활성 사용자(7d), 총 세션 수, 평균 syncRate | 🛡️ |
| BE-05 | GET | `/admin/stats/sessions?from=&to=` | 일별 세션 수 시계열 | 🛡️ |
| BE-05 | GET | `/admin/stats/exercises` | 운동별 인기도 (세션 수 top) | 🛡️ |

### 새 endpoint 없는 작업 (백그라운드/스키마 변경)

| 작업 | 변경 형태 |
|------|---------|
| BE-03 | gRPC `CompleteAnalysis` 콜백 핸들러에서 비동기 트리거 — REST 표면 없음. Report 가 자동 생성되어 기존 `GET /reports/sessions/{id}` 응답에 LLM 텍스트 채워짐 |
| BE-09 (보류) | DB 컬럼 + proto 메시지 확장만. 새 endpoint 없음, 기존 `/exercises/sessions/...` 응답 필드만 확장 |
| **BE-10** (H2 부속) | `AiHealthCheckService` 신설 + gRPC client interceptor. REST 표면 없음. `application.yml` 의 `resilience4j.*` 설정 |
| **BE-11** (H2 부속) | `PoseDataValidationGate` 신설 — `ExerciseGrpcService.SavePoseDataBatch` 호출 전 검증. REST 표면 없음 |
| **BE-12** (H2 부속, 선택) | `PoseDataIngestService` + `pose_data_outbox` 테이블. 콜백 처리를 Outbox 패턴으로 비동기화 |

### 합계
- **신설 endpoint**: 사용자용 10개 + 관리자용 4~7개 (BE-04 A/B 선택에 따라) = **14~17개**
- **응답 보강만**: 2개 (`GET /reports/sessions/{id}`, 기존 세션 응답)
- **REST 표면 없음**: 5개 작업 (BE-03 LLM 트리거, BE-09 스키마 확장, BE-10·11·12 H2 부속)
- **폐기**: 1개 (BE-01)

### 인증/권한 메모
- 👤 사용자용 — 기존 `JwtAuthFilter` 통해 `Authentication` 주입, `memberId` 추출. 소유권 검증(자기 자신 sessionId/goalId만 접근) 필수
- 🛡️ 관리자용 — Member 에 role/admin 필드 확인 필요 (BE-05 리스크 항목 참조). 없으면 추가 작업 (BE-05 의 선행 작업 ~1h)
- 🤖 H2 채택으로 AI 측 `POST /pose` 가 `INTERNAL_API_TOKEN` 검증 (분기 I1 잠정). 프론트가 이 토큰을 어떻게 받는지는 분기 I 결정에 따라 정해짐

---

## 관련 문서
- [`21-task-assignment.md`](./21-task-assignment.md) — 원본 작업 분배표 (BE-01~09 의 요약 행)
- [`20-feature-roadmap.md`](./20-feature-roadmap.md) — PPT 요구사항 ↔ 코드 매핑
- [`decisions/ai-backend-coupling.md`](../decisions/ai-backend-coupling.md) — 분기 H 등 미결 결정
- [`architecture/ai-backend-integration.md`](../architecture/ai-backend-integration.md) — 현재 결합 현황
