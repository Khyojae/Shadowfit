## 🎯 운동 세션 네트워크 타임아웃 처리 - 구현 완료

사용자의 제안하신 네트워크 장애 처리 전략이 완전히 구현되었습니다.

---

### 📌 핵심 전략 평가

**사용자 제안:**
> 예상 운동시간대비 30분 초과 시까지 FastAPI 분석 결과 미수신 시, 
> 스프링이 상태처리를 '진행 중'에서 '실패' 처리

**평가: ⭐⭐⭐⭐⭐ 매우 우수함**

**장점:**
- ✅ **자동화**: 수동 개입 없이 자동으로 타임아웃 처리
- ✅ **데이터 손실 방지**: 세션 레코드는 DB에 보존됨 (FAILED 상태)
- ✅ **명확한 상태 표시**: 사용자가 "실패"를 인식하고 재시도 가능
- ✅ **설정 가능한 타임아웃**: 운동별로 예상시간 조정 가능
- ✅ **버퍼 시간 설정 가능**: 느린 네트워크 대응 가능
- ✅ **효율적인 DB 쿼리**: FETCH JOIN으로 N+1 문제 해결

---

### 📦 구현 완료 항목

#### 1️⃣ **Status 열거형 확장**
```
파일: model/exercise/Status.java
추가: FAILED 상태
```

#### 2️⃣ **Exercise 모델 확장**
```
파일: model/exercise/Exercise.java
추가: expectedDurationMinutes (예상 운동시간)
기본값: 15분
```

#### 3️⃣ **SessionTimeoutScheduler 생성**
```
파일: service/Exercise/SessionTimeoutScheduler.java
기능:
  • 매 1분마다 IN_PROGRESS 세션 모니터링
  • 타임아웃 계산: startTime + expectedDurationMinutes + 30분
  • 타임아웃된 세션을 FAILED로 변경
  • 로깅: 타임아웃 사유 기록
```

#### 4️⃣ **SessionRepository 업그레이드**
```
파일: repository/exercise/SessionRepository.java
추가: findByStatus(Status) - FETCH JOIN으로 최적화
```

#### 5️⃣ **스케줄러 활성화**
```
파일: ShadowfitApplication.java
추가: @EnableScheduling
```

#### 6️⃣ **SchedulerConfig 설정**
```
파일: global/config/SchedulerConfig.java
설정:
  • ThreadPoolTaskScheduler (5개 스레드)
  • 우아한 종료 처리
```

#### 7️⃣ **application.yml 설정**
```yaml
exercise:
  session:
    timeout:
      default-buffer-minutes: 30      # 타임아웃 버퍼
      check-interval-minutes: 1       # 체크 간격
```

#### 8️⃣ **데이터베이스 스키마 업데이트**  
```sql
-- exercises 테이블
ALTER TABLE exercises ADD COLUMN expected_duration_minutes INT DEFAULT 15;

-- exercise_sessions 테이블
ALTER TABLE exercise_sessions 
  MODIFY COLUMN status ENUM('IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'FAILED');
```

#### 9️⃣ **테스트 코드**
```
파일: test/java/com/shadowfit/service/Exercise/SessionTimeoutSchedulerTest.java
테스트 케이스:
  • 타임아웃된 세션 → FAILED 변경
  • 타임아웃되지 않은 세션 → 유지
  • IN_PROGRESS 없음 → 아무 작업 없음
  • 운동별 예상시간 적용
  • 단기 운동 타임아웃 계산
```

#### 🔟 **구현 가이드 문서**
```
파일: docs/15-session-timeout-guide.md
내용:
  • 시스템 아키텍처
  • 타임아웃 계산식
  • 동작 플로우
  • 설정 커스터마이제이션
  • 향후 확장 기능
  • 문제 해결 가이드
```

---

### 🔄 동작 흐름

```
운동 시작
  ↓
Session 생성 (Status: IN_PROGRESS, startTime: now, createdAt: now)
  ↓
gRPC로 FastAPI에 분석 요청
  ↓
┌─────────────────────────────────┐
│ 경로 A: 정상                     │
│ FastAPI 분석 완료 → 응답 수신   │
│ Status: COMPLETED               │
│ ✓ 성공                          │
└─────────────────────────────────┘
  또는
┌──────────────────────────────────────┐
│ 경로 B: 네트워크 장애                │
│ 1시간 경과                           │
│ SessionTimeoutScheduler 실행         │
│   → 타임아웃 확인                    │
│   → (15분 + 30분 = 45분 < 60분)     │
│   → Status: FAILED로 변경           │
│   → endTime 설정                     │
│ ✓ 자동 처리                         │
└──────────────────────────────────────┘
```

---

### 📊 타임아웃 시간 예시

| 운동 | 예상시간 | 버퍼 | 타임아웃 | 결과 |
|-----|---------|------|---------|------|
| 스쿼트 | 15분 | 30분 | 45분 | 45분 이후 FAILED |
| 플랭크 | 10분 | 30분 | 40분 | 40분 이후 FAILED |
| 에어로빅 | 30분 | 30분 | 60분 | 60분 이후 FAILED |
| 런지 | 20분 | 30분 | 50분 | 50분 이후 FAILED |

---

### 🔒 동시성 처리 (낙관적 락)

#### 문제 상황

`SessionTimeoutScheduler`(스프링)와 `completeAnalysis` gRPC 콜백(FastAPI)이 **같은 세션을 동시에 갱신**하려고 할 때 데이터 정합성 문제가 발생할 수 있습니다.

```
[충돌 시점]
  Spring 스케줄러: IN_PROGRESS → FAILED  
  FastAPI 콜백  : IN_PROGRESS → COMPLETED + 운동 기록
                ↓
        어느 쪽이 마지막에 commit하느냐에 따라
        실제 운동 데이터가 사라질 수 있음 ⚠️
```

#### 해결 전략: `@Version` 기반 낙관적 락

**Session 엔티티**에 버전 컬럼을 추가하여, JPA가 UPDATE 시 `WHERE id=? AND version=?`을 자동으로 붙입니다. 다른 트랜잭션이 먼저 커밋해 버전이 바뀌었으면 `ObjectOptimisticLockingFailureException`이 발생합니다.

```java
// Session.java
@Version
@Column(nullable = false)
private Long version = 0L;
```

```sql
-- schema.sql
ALTER TABLE exercise_sessions ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
```

#### 시나리오별 처리

##### 1-1) 정상 케이스 (충돌 없음)
FastAPI가 타임아웃 전에 결과 도착 → COMPLETED + 기록 저장 (v=0 → v=1)

##### 1-2) 30분 경계에서 동시 발생 (찰나의 충돌)

| 시각 | 스케줄러 | FastAPI 콜백 | DB |
|---|---|---|---|
| T | findById (v=0) 로드 | findById (v=0) 로드 | IN_PROGRESS, v=0 |
| T+ε | FAILED set | COMPLETED + 기록 set | IN_PROGRESS, v=0 |
| T+δ | saveAndFlush 성공 | — | **FAILED, v=1** |
| T+2δ | — | saveAndFlush → `OptimisticLockException` (v=0 기대했지만 v=1) | FAILED, v=1 |
| T+3δ | — | **재시도**: findById (v=1, FAILED) → COMPLETED + 기록 → saveAndFlush | **COMPLETED, v=2** |

→ 스케줄러가 먼저 commit 했어도 **사용자 운동 데이터는 절대 유실되지 않음**

##### 1-3) 30분 경과 후 늦게 재연결 (충돌 없음)

```
T+0   세션 시작                              IN_PROGRESS, v=0
T+45  스케줄러 → FAILED                      FAILED, v=1
T+60  FastAPI 뒤늦게 결과 도착
        findById (v=1, FAILED) 로드
        COMPLETED + 기록 set  
        saveAndFlush                         COMPLETED, v=2
```

→ 이미 스케줄러가 commit을 끝낸 상태이므로 충돌이 발생하지 않습니다. FastAPI가 가져온 시점의 v=1을 그대로 갱신해 v=2가 됩니다.

**결과**: 진행중인 운동 세션은 꺼지고(FAILED 거쳐) → 운동 기록이 DB에 저장됨(COMPLETED) ✅

#### 구현 핵심

| 위치 | 처리 |
|---|---|
| `SessionTimeoutScheduler` | 충돌 시 `ObjectOptimisticLockingFailureException` catch → 양보 (FastAPI 결과 우선) |
| `SessionService.completeSession` | 충돌 시 최대 3회 재시도 → 재조회 후 COMPLETED + 기록 덮어쓰기 |
| `ExerciseAnalysisService.completeSession` | 동일한 재시도 로직 (앱→Spring 경로) |
| 트랜잭션 분리 | 스케줄러는 세션별 독립 트랜잭션(`SessionService.markAsFailedIfStillInProgress`) 호출 → 한 세션의 충돌이 다른 세션 처리를 막지 않음 |
| 자기 주입 (`@Lazy`) | 같은 클래스 내부 메서드 호출 시에도 Spring 프록시를 거치도록 하여 `@Transactional` 적용 보장 |

#### 동시성 테스트 검증

```
✅ testYieldOnOptimisticLockConflict
   → 두 세션 중 한 세션 충돌 시 다른 세션은 정상 처리됨

✅ testYieldWhenAlreadyCompleted
   → markAsFailed가 false 반환 시(이미 COMPLETED) 정상 진행
```

---

### 🔁 멱등성 처리 (FastAPI ↔ Spring 응답 신호 유실)

#### 문제 상황

gRPC 통신은 양방향이라 어느 쪽 신호든 유실될 수 있습니다.

##### 2-1) FastAPI → Spring 요청 신호 유실
```
FastAPI: 결과 메모리 보관 → Spring에 전송 → 응답 미수신
       ↓
       (네트워크 복구 후) 같은 결과 재전송
```

##### 2-2) Spring → FastAPI 응답 신호 유실
```
Spring: DB에 결과 저장 완료 → 응답 송신 중 네트워크 끊김
FastAPI: 응답 미수신 → 같은 결과 재전송
```

→ **두 케이스 모두 Spring 입장에서는 "같은 sessionId로 completeAnalysis가 두 번 들어오는 상황"** 입니다.

#### 멱등성 미적용 시 발생 문제

| 항목 | 문제 |
|---|---|
| 첫 `endTime` 손실 | 두 번째 호출 시각으로 덮어써짐 |
| 불필요한 DB write | 같은 데이터를 다시 저장 |
| `version` 무의미한 증가 | 낙관적 락 카운터가 중복 호출마다 올라감 |
| 자기 자신과 충돌 가능 | 동시 재전송 시 OptimisticLockException 발생 |

#### 해결: 진입점에서 status 체크

```java
@Transactional
public void applyComplete(SessionCompleteRequest request) {
    Session session = sessionRepository.findById(request.getSessionId())
            .orElseThrow(...);

    // 멱등성: 이미 COMPLETED면 첫 완료 시각/기록 보존하고 즉시 종료
    if (session.getStatus() == Status.COMPLETED) {
        return;
    }

    session.setStatus(Status.COMPLETED);
    // ... 기록 저장
}
```

#### Status별 동작 정리

| 진입 시 status | 동작 | 설명 |
|---|---|---|
| `IN_PROGRESS` | 정상 완료 처리 | 가장 일반적인 케이스 |
| `FAILED` | COMPLETED + 기록으로 덮어쓰기 | 시나리오 1-2/1-3 (스케줄러 타임아웃 후 도착) |
| `COMPLETED` | **즉시 return (no-op)** | **시나리오 2-1/2-2 (재전송)** |
| `CANCELED` | 현재는 덮어씀 (기존 동작 유지) | 사용자 명시적 취소 — 추후 정책 결정 필요 |

#### 효과

- ✅ 첫 완료 시각(`endTime`) 보존
- ✅ FastAPI는 안심하고 재전송 가능 (At-Least-Once 보장 가능)
- ✅ 동일 데이터로 인한 OptimisticLock 충돌 방지
- ✅ 가입점이 두 곳(`SessionService`, `ExerciseAnalysisService`)이지만 동일 정책 적용

---

### 🧪 테스트 검증

모든 테스트가 성공적으로 수행되었습니다:

```
✅ testTimeoutSessionChangedToFailed
   → 타임아웃된 세션이 FAILED로 변경됨

✅ testNonTimeoutSessionUnchanged
   → 타임아웃되지 않은 세션은 유지됨

✅ testNoInProgressSessionsDoesNothing
   → IN_PROGRESS 세션이 없으면 아무 작업 없음

✅ testTimeoutBasedOnExerciseDuration
   → 운동별 예상시간이 적용됨

✅ testQuickExerciseTimeoutAfter40Minutes
   → 단기 운동의 타임아웃이 정확히 계산됨
```

---

### 🛠️ 사용 고시

#### 1. 운동별 예상 시간 설정 (필수)
```sql
-- 초기 데이터 설정
UPDATE exercises SET expected_duration_minutes = 15 WHERE name = '스쿼트';
UPDATE exercises SET expected_duration_minutes = 10 WHERE name = '플랭크';
UPDATE exercises SET expected_duration_minutes = 20 WHERE name = '런지';
UPDATE exercises SET expected_duration_minutes = 30 WHERE name = '에어로빅';
```

#### 2. 타임아웃 버퍼 조정 (선택)
```yaml
# application.yml
exercise:
  session:
    timeout:
      default-buffer-minutes: 45  # 기본 30분 → 45분으로 변경
```

#### 3. 체크 간격 조정 (선택)
```yaml
# 기본: 1분마다 체크
# 더 자주 체크하려면
exercise:
  session:
    timeout:
      check-interval-minutes: 0.5  # 30초마다 체크
```

---

### 📁 파일 변경 요약

| 파일 | 변경 내용 |
|-----|---------|
| Status.java | FAILED 상태 추가 |
| Exercise.java | expectedDurationMinutes 필드 추가 |
| Session.java | **@Version 필드 추가 (낙관적 락)** |
| SessionTimeoutScheduler.java | **신규 생성** + 충돌 양보 처리 |
| SessionService.java | **completeSession 재시도 + markAsFailedIfStillInProgress 헬퍼** |
| ExerciseAnalysisService.java | **completeSession 재시도 로직** |
| SessionRepository.java | findByStatus() 메서드 추가 |
| ShadowfitApplication.java | @EnableScheduling 추가 |
| SchedulerConfig.java | **신규 생성** |
| application.yml | 타임아웃 설정 추가 |
| schema.sql | 스키마 업데이트 + **version 컬럼** |
| data.sql | 데이터 초기화 수정 |
| SessionTimeoutSchedulerTest.java | **신규 생성** + 동시성 충돌 테스트 |
| 15-session-timeout-guide.md | **신규 생성** (문서) |

---

### 💡 향후 확장 가능성

1. **사용자 알림**
   ```java
   notificationService.notifySessionTimeout(session);
   ```

2. **자동 재시도**
   ```java
   session.setStatus(Status.PENDING_RETRY);
   ```

3. **모니터링 & 관리자 알림**
   ```java
   if (timeoutCount > 10) {
       adminService.notifyHighTimeoutRate(timeoutCount);
   }
   ```

4. **부분 결과 저장**
   - 네트워크 복구 후 부분 데이터 활용

5. **머신러닝 기반 예상시간 조정**
   - 사용자별/난이도별 평균 시간 학습

---

### ✅ 최종 체크리스트

- [x] Status ENUM에 FAILED 추가
- [x] Exercise에 expectedDurationMinutes 필드 추가
- [x] SessionTimeoutScheduler 구현
- [x] SessionRepository에 findByStatus() 추가
- [x] @EnableScheduling 적용
- [x] SchedulerConfig 설정
- [x] application.yml 설정 추가
- [x] 데이터베이스 스키마 업데이트
- [x] 테스트 코드 작성 및 검증
- [x] 구현 가이드 문서 작성
- [x] 빌드 성공 확인
- [x] **Session @Version 추가 (낙관적 락)**
- [x] **스케줄러 ↔ FastAPI 동시 갱신 충돌 처리**
- [x] **completeSession 재시도 로직 (FAILED → COMPLETED 덮어쓰기)**
- [x] **동시성 테스트 케이스 추가**
- [x] **completeSession 멱등성 처리 (재전송 대응 - 시나리오 2-1, 2-2)**

---

### 🚀 배포 준비

현재 상태: **프로덕션 배포 가능**

배포 시 체크사항:
1. 운동별 예상시간 데이터 마이그레이션
2. 기존 IN_PROGRESS 세션 처리 계획
3. 모니터링 설정
4. 로그 레벨 확인

---

### 📞 문의 사항

타임아웃 튜닝이 필요한 경우:
- 버퍼 시간 조정: `default-buffer-minutes`
- 체크 간격 조정: `check-interval-minutes`
- 운동별 시간 조정: `exercises.expected_duration_minutes`

---

**구현 완료 날짜**: 2026년 5월 8일  
**상태**: ✅ 준비 완료



