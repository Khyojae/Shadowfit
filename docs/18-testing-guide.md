# 테스트 가이드

마지막 업데이트: 2026-05-23
범위: Spring 백엔드의 테스트 실행 방법, 테스트 환경 구성, 현재 보유 테스트 인벤토리.

---

## 1. 빠르게 실행 (Spring 백엔드)

```bash
# 프로젝트 루트에서
./gradlew test          # 모든 테스트
./gradlew build         # 빌드 + 테스트
```

Windows PowerShell:
```powershell
.\gradlew.bat test
.\gradlew.bat build
```

**Docker 컨테이너 불필요** — 테스트는 H2 인메모리 DB 로 동작 (2026-05-09 정비, 커밋 c7657f1 인접 작업).

---

## 2. 테스트 환경 구성

### 2.1 별도 `application.yml` (`src/test/resources/application.yml`)

| 항목 | 운영 (`src/main/resources/application.yml`) | 테스트 |
|------|------|------|
| DB | MySQL (`shadowfit-mysql:3306`) | H2 인메모리 (`jdbc:h2:mem:shadowfit_test`) |
| `sql.init.mode` | 스키마/시드 자동 실행 | `never` — JPA `@Entity` 가 직접 생성 |
| `jpa.hibernate.ddl-auto` | `validate` (또는 비활성) | `create-drop` |
| `jpa.database-platform` | MySQLDialect | H2Dialect (`MODE=MySQL` 호환) |
| gRPC 포트 | 6565 (서버), `shadowfit-ai:8585` (클라이언트) | 0 (서버 비활성), `localhost:0` (클라이언트 비활성) |
| `JWT_SECRET` | env 필수 | 더미 문자열 |
| `INTERNAL_API_TOKEN` | env 필수 | `test-internal-token` |

> H2 `MODE=MySQL` 옵션으로 MySQL 전용 syntax 대부분 호환. ENUM 도 자동 처리.

### 2.2 build.gradle 의존성

```gradle
testImplementation 'org.springframework.boot:spring-boot-starter-test'
testImplementation 'org.junit.jupiter:junit-jupiter'
testRuntimeOnly 'com.h2database:h2'         // 2026-05-09 추가
```

---

## 3. 현재 보유 테스트 인벤토리

```
backend/src/test/java/com/shadowfit/
├── ShadowfitApplicationTests.java                          # 컨텍스트 로드 확인 (sanity)
└── service/Exercise/SessionTimeoutSchedulerTest.java       # 단위 테스트
```

### 3.1 `SessionTimeoutSchedulerTest`
**대상**: `SessionTimeoutScheduler.checkAndTimeoutSessions()`

**검증 케이스**:
- 타임아웃된 세션이 `FAILED` 로 변경됨
- 타임아웃 전 세션은 유지됨
- `IN_PROGRESS` 세션 없을 때 no-op
- 운동별 `expectedDurationMinutes` 가 정확히 반영됨
- 단기 운동 (10분짜리) 타임아웃 계산
- `ObjectOptimisticLockingFailureException` 충돌 시 양보 처리

**방식**: Mockito 로 `SessionRepository`, `SessionService` mock → 스케줄러 단독 검증. 컨텍스트 로드 없음 → 빠름.

자세한 시나리오는 [`15-session-timeout-guide.md`](./15-session-timeout-guide.md) §🔒 동시성 처리 참조.

### 3.2 `ShadowfitApplicationTests`
Spring 컨텍스트 정상 로드 확인. `@SpringBootTest` 사용 → 테스트 `application.yml` 적용되는지 검증 역할도 겸함.

---

## 4. 테스트 분류 (현재 + 권장)

| 종류 | 현재 | 권장 |
|------|------|------|
| 단위 테스트 (Mock) | `SessionTimeoutSchedulerTest` | 새 서비스 추가 시 의무 |
| 통합 테스트 (`@SpringBootTest`) | `ShadowfitApplicationTests` 만 | controller·gRPC 클라이언트별 추가 검토 |
| Repository 테스트 (`@DataJpaTest`) | 없음 | 복잡한 JPQL 쿼리 (`SessionRepository.findByStatus` FETCH JOIN 등) 검증 시 |
| gRPC 통합 테스트 | 없음 | `InProcessChannel` 활용, [`decisions/ai-backend-coupling.md`](./decisions/ai-backend-coupling.md) 분기 A 진척 후 |
| E2E 테스트 | 없음 | Docker Compose + Testcontainers 검토 대상 |

---

## 5. 테스트 작성 패턴

### 5.1 단위 테스트 (서비스/스케줄러)
```java
@DisplayName("...설명...")
class XxxServiceTest {
    @Mock private SomeRepository repo;
    @Mock private OtherService dep;
    private XxxService target;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        target = new XxxService(repo, dep);
    }

    @Test
    @DisplayName("정상 케이스 — A 이면 B 한다")
    void normalCase() {
        // given
        when(repo.findById(1L)).thenReturn(Optional.of(...));
        // when
        target.doSomething(1L);
        // then
        verify(repo).save(any());
    }
}
```

### 5.2 통합 테스트 (컨텍스트 필요)
```java
@SpringBootTest
@ActiveProfiles("test")     // 명시 안 해도 src/test/resources/application.yml 자동 적용
class XxxIntegrationTest {
    @Autowired private XxxService service;
    // ...
}
```

### 5.3 낙관적 락 충돌 시뮬레이션
실제 충돌은 `EntityManager.flush()` 로 강제. `SessionTimeoutSchedulerTest` 의 동시성 테스트 케이스 참조.

---

## 6. 트러블슈팅

### 6.1 "Unable to determine Dialect without JDBC metadata"
- **원인**: 테스트가 운영 `application.yml` 을 잡고 `shadowfit-mysql` 호스트로 접근 시도
- **해결**: `src/test/resources/application.yml` 이 클래스패스에 있는지 확인. `@SpringBootTest` 가 자동으로 우선 적용.

### 6.2 "Failed to load ApplicationContext"
- 가장 흔한 원인: 새 `@Component` 빈이 env (`JWT_SECRET` 등) 필수인데 테스트 yml에 더미값 누락
- 해결: `src/test/resources/application.yml` 의 `jwt:`, `internal:` 등 더미값 절 확장

### 6.3 gRPC 클라이언트 빈 로드 실패
- 테스트 yml 의 `grpc.client.fastapi-client.address: 'static://localhost:0'` 가 가짜 주소. 실제 호출은 stub 으로 mock 해야 함 (`@MockBean`).

### 6.4 H2 와 MySQL 의 ENUM 차이
- `MODE=MySQL` 로 대부분 호환되지만 일부 JSON 함수는 미지원. JSON 컬럼은 `String` 으로 저장하면 무난.

---

## 7. CI/CD (현재 상태)

> **메모**: 현재 명시적 CI 파이프라인 정의(`*.yml` GitHub Actions, GitLab CI 등) 가 저장소에 없는 것으로 보임. `./gradlew test` 가 통과한다는 것은 로컬 수동 검증 단계.
>
> 다음 작업 후보 (도입 시):
> - `.github/workflows/test.yml` — PR마다 `./gradlew test` 자동 실행
> - 커버리지 리포트 (`jacoco`)
> - PR 머지 시 main 브랜치 빌드 후 Docker 이미지 push

CI 도입은 [`decisions/`](./decisions/) 에 별도 결정 문서로 다룰 만함.

---

## 8. 수동 e2e 검증 절차 (스쿼트 1사이클)

코드 정적 분석으로는 보장 못 하는 결합 상태를 한 번에 검증. 카메라 + 프론트 + AI + Spring + MySQL 전부 띄운 상태에서 진행.

### 8.1 사전 준비

```bash
# 1) Docker 컨테이너 기동
docker compose up -d --build
docker compose ps                  # mysql 'healthy', backend·ai 'Up'

# 2) DB 상태 초기화 확인 (필요 시)
docker exec -it shadowfit-mysql mysql -u shadowfit -p shadowfit -e "
  SELECT COUNT(*) AS sessions FROM exercise_sessions;
  SELECT COUNT(*) AS poses FROM pose_data;
"

# 3) 프론트 기동 (별도 터미널)
cd frontend && npx expo start
```

### 8.2 한 사이클 — 검증 포인트

| 단계 | 동작 | 검증 |
|------|------|------|
| 1 | 로그인 → JWT 발급 | `Authorization: Bearer ...` 보유 |
| 2 | 운동 화면 진입, "스쿼트" 선택 → 시작 버튼 | `POST /exercises/sessions` 호출, 응답 `{sessionId, status:IN_PROGRESS}` |
| 3 | 카메라 켜짐, 스쿼트 5회 천천히 수행 | 프론트가 프레임마다 `POST /pose` 호출 (네트워크 탭 확인) |
| 4 | rep 1 완성 시점 | Spring 로그에 `[AI → Spring] PoseData 배치 전송 (session=X, count=N, success=true)` |
| 5 | 5 rep 완료 후 종료 버튼 | `PUT /exercises/sessions/{id}/stop` 호출, 즉시 202 |
| 6 | 종료 후 1~2초 | Spring 로그에 `[AI → Spring] CompleteAnalysis 성공 (session=X, status=COMPLETED, attempt=1)` |

### 8.3 DB 검증 (운동 끝난 직후)

```sql
-- 1. 세션 상태
SELECT id, status, total_reps, avg_sync_rate, max_sync_rate, min_sync_rate, start_time, end_time, version
FROM exercise_sessions
ORDER BY id DESC LIMIT 1;
-- 기대: status=COMPLETED, total_reps>=1, end_time 채워짐, version>=1

-- 2. rep 단위 포즈 데이터
SELECT session_id, COUNT(*) AS frame_count, MIN(sync_rate) AS min_sr, MAX(sync_rate) AS max_sr, MIN(timestamp_sec) AS first_t, MAX(timestamp_sec) AS last_t
FROM pose_data
WHERE session_id = (SELECT MAX(id) FROM exercise_sessions);
-- 기대: frame_count > 0, sync_rate 값 채워짐, timestamp_sec 시계열 형성

-- 3. 피드백 발화 로그 (TTS 사용 시)
SELECT session_id, feedback_type, sync_rate_at_trigger, occurred_at
FROM session_feedback_logs
WHERE session_id = (SELECT MAX(id) FROM exercise_sessions)
ORDER BY occurred_at;
-- 기대: 발화된 피드백이 있다면 N행
```

### 8.4 자동 통합 테스트로 같은 흐름 검증

Spring 측 `backend/src/test/java/com/shadowfit/integration/ExerciseSessionFlowIntegrationTest.java` 에 위 시퀀스의 자동화 버전이 있음. AI 서버는 mock 으로 대체, 콜백을 직접 시뮬레이션.

```bash
./gradlew test --tests "ExerciseSessionFlowIntegrationTest"
```

수동 e2e 와 자동 테스트의 역할 분리:
- **자동 테스트**: Spring 측 결합·DB 상태·동시성·멱등성 로직 — 코드 변경 시 자동 회귀 검증
- **수동 e2e**: 프론트 ↔ AI ↔ Spring 의 실제 통신·카메라·MediaPipe·gRPC 채널 — 사람이 한 번 돌려봐야 보이는 것

### 8.5 실패 시나리오 점검 체크리스트

수동 e2e 한 번 더 돌려서 비정상 경로도 확인 권장:

- [ ] 종료 버튼 누르기 전에 앱 강제 종료 → 1분 후 스케줄러가 `status=FAILED` 로 떨어뜨리는지 확인
- [ ] AI 컨테이너를 운동 중 죽이기 (`docker compose stop shadowfit-ai`) → 콜백 안 옴 → 타임아웃까지 IN_PROGRESS → FAILED 전환 확인
- [ ] AI 컨테이너 복귀 후 같은 sessionId 로 콜백이 늦게 와도 멱등성으로 처리 (FAILED → COMPLETED 덮어쓰기) 확인
- [ ] 같은 session 에 동시에 `/stop` 두 번 → 한 번만 처리, OptimisticLock 충돌 시 재시도 동작

자세한 시나리오는 [`15-session-timeout-guide.md`](./15-session-timeout-guide.md) §🔒 동시성 / §🔁 멱등성 참조.

### 8.6 트러블슈팅

| 증상 | 원인 후보 | 확인 |
|------|---------|------|
| `pose_data` 빈 채로 끝남 | 프론트가 `POST /pose` 안 부름 | 프론트 네트워크 탭, `frontend/app/(tabs)/exercise.tsx` 카메라 프레임 송신 코드 |
| `status=IN_PROGRESS` 영원히 | AI 콜백 영구 실패 (3회 재시도 후) | `docker logs shadowfit-ai` 의 ERROR 로그, gRPC 채널·토큰 확인 |
| `UNAUTHENTICATED` gRPC 에러 | 양쪽 `INTERNAL_API_TOKEN` 불일치 | `.env` 의 토큰 vs 두 컨테이너 환경변수 |
| `status=FAILED` 즉시 떨어짐 | `expectedDurationMinutes` 가 너무 짧음 | `SELECT name, expected_duration_minutes FROM exercises` |
| 프론트가 `/complete` 부름 | 옛 디프리케이트 경로 미마이그레이션 | 프론트 종료 버튼 핸들러 (`exercise.tsx`) |

---

## 9. AI 서버 (Python) 테스트

코드상 디렉터리는 있지만 본 가이드 범위 밖. AI 측 테스트 변경은 [`feedback-minimize-python-changes`](../../C:/Users/khjae/.claude/projects/E--init/memory/feedback_minimize_python_changes.md) 정책에 따라 원작자 영역.

기존 파일 참고용:
- `ai-server/tests/test_camera.py`, `test_reference_builder.py`, `test_squat_analyzer.py`, `test_sync_feedback.py`, `test_pose_filter.py`
- 실행: `cd ai-server && pytest`

---

## 관련 문서
- 동시성·멱등성 테스트 시나리오 → [`15-session-timeout-guide.md`](./15-session-timeout-guide.md)
- 에러 코드 (테스트에서 검증할 대상) → [`17-error-codes.md`](./17-error-codes.md)
- 배포 절차 → [`19-deployment.md`](./19-deployment.md)
