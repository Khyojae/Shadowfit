# Decision: Redis 도입 — *MySQL 로 부족한지* 엄격한 증명

상태: **✅ CLOSED — 추천 A 채택 (2026-05-25): 도입 보류 박제. T1~T5 trigger 발생 시 재검토**
작성: 2026-05-25
배경: 사용자 질문 "지금 서비스에서 Redis 다는 거 검토 — MySQL 만으로 부족한 거 증명 엄격하게" (2026-05-25). 백엔드 채용 시그널([[user_career_target]]) 에서 Redis 가 단골이지만, *근거 없는 도입은 면접에서 들킴*. 진짜 필요성 vs 어필 의도를 분리해 박제.
연관: [`./ai-backend-coupling.md`](./ai-backend-coupling.md) 분기 D, [`./ai-load-budget.md`](./ai-load-budget.md), [`../tasks/25-portfolio-strategy.md`](../tasks/25-portfolio-strategy.md)

---

## 1. 평가 프레임 — *어떤 조건 충족 시* 도입 가치 있는가?

**한 가지라도 충족** 해야 도입 정당화. 셋 다 미충족 = **도입 보류**.

| 기준 | 의미 | 측정·증명 방법 |
|------|------|--------------|
| **(A) 정확성/일관성** | MySQL 로 *원천 불가능* 한 의미가 있는가 (분산 락, 휘발성 카운터, 다중 인스턴스 동기화) | 다중 인스턴스 운영 *이미 시작* 또는 단일 인스턴스 한정으로 *기능 깨짐* 사례 |
| **(B) 성능 한계** | MySQL 측정 결과가 SLO 초과해 캐싱 *외 다른 방법으로 못 푸는* 상황 | baseline 측정 → MySQL 인덱스·쿼리 튜닝 *후에도* p95 > 임계값 |
| **(C) 확장성 강제** | 단일 인스턴스 운영 가정이 *이미 깨졌거나* 운영 단계에 도달 | AI/Spring 다중 인스턴스 운영 시작 시점, 또는 분기 D (`ai-backend-coupling.md`) D1→D2 격상 |

엄격성 원칙:
- *예측 부족함* (가설) 만으론 부족. **현재 코드의 실제 한계** 또는 **측정 데이터** 필요
- "도입하면 좋을 것 같다" 식 추정 거부 — 그건 어필 의도지 *필요성 증명* 아님
- 모든 trade-off 명시 (운영 비용 ↑, 데이터 일관성 복잡도 ↑, 학습 곡선)

---

## 2. 현재 코드의 Redis 후보 use case — 7개

### 2.1 JwtBlacklist (로그아웃 토큰 무효화)

**현재 구현** ([`JwtBlacklist.java`](../../backend/src/main/java/com/shadowfit/global/security/jwt/JwtBlacklist.java)):
```java
private final Map<String,Long> blacklist = new ConcurrentHashMap<>();
@Scheduled(fixedDelay = 3600000) public void cleanup() { ... }
```
- in-memory `ConcurrentHashMap` + 매시간 cleanup 스케줄러

**MySQL 만으로 풀 수 있는가**: ✅ 가능 (jwt_blacklist 테이블 + index on token + TTL cleanup batch)

**부족함 증명**:
| 기준 | 증명 시도 | 판정 |
|---|---|---|
| (A) 정확성 | 단일 인스턴스 한정이라 *현재 깨지지 않음*. 다중 인스턴스 전환 시에만 동기화 깨짐 | ❌ 미증명 |
| (B) 성능 | `ConcurrentHashMap.containsKey` ~O(1) ns 수준. MySQL `SELECT 1 WHERE token=?` p95 측정 안 됐지만 인덱스 있으면 <2ms 예상 | ❌ 미증명 |
| (C) 확장성 | 현재 단일 인스턴스 운영. 다중 인스턴스 운영 계획 없음 (MVP 단계) | ❌ 미증명 |

**리스크 1개 존재**: 서버 재시작 시 blacklist 손실 → 로그아웃했던 토큰이 *남은 expiry 만큼* 다시 유효. 보안 이슈 *작음* (재시작 빈도 낮음 + access token 24h 짧음).

→ **Redis 가치 약함**. *MySQL persist* 만으로도 재시작 문제 해결 가능.

---

### 2.2 AI session_state (운동 분석 상태)

**현재 구현** ([`session_state.py`](../../ai-server/app/grpc/session_state.py)):
```python
class SessionStateRegistry:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._sessions: dict[int, SessionState] = {}
```
- AI 1 인스턴스 process 메모리 + threading.Lock

**MySQL 만으로 풀 수 있는가**: ❌ 어려움. *매 프레임* 갱신 (10fps) + landmark/angle 누적 — MySQL row 수정으로는 부담 큼

**부족함 증명**:
| 기준 | 증명 시도 | 판정 |
|---|---|---|
| (A) 정확성 | AI 재시작 시 *진행 중 세션 전체 손실*. 이미 일어남 — 분기 D1 (in-memory) 이 *이 손실을 의식적으로 감수* 한 결정 | ⚠️ 분기 D1 결정으로 *허용* 됨 |
| (B) 성능 | Redis 도 in-memory이므로 *성능 측면에선 동등*. 차이는 *프로세스 분리* | n/a |
| (C) 확장성 | AI 수평 확장 불가의 진짜 원인. 분기 D 가 *이미 외부화 = D2* 옵션 인지 | ⚠️ 분기 D1 채택 (MVP 한정) |

**핵심 — 분기 D 와의 관계**: [`ai-backend-coupling.md`](./ai-backend-coupling.md) 분기 D 가 *이미 검토했고 D1 (in-memory) 채택*. 즉 **AI session_state 외부화는 분기 D2 결정 사항이지 본 문서 결정 사항 아님**. 본 문서가 D2 격상을 *권고* 하는 건 분기 D 정신 위반.

→ **Redis 가치 = 분기 D 의 격상 trigger 발생 후에만**. 현재 trigger (다중 사용자 N>?) 측정 안 됨 — 도입 근거 없음.

---

### 2.3 페르소나 피드백 템플릿 캐시

**현재 흐름** (BE-13 결과):
- 클라가 운동 시작 시 `GET /exercises/{id}/feedback-templates` 1회 호출
- Service 가 `ExerciseFeedbackTemplateRepository.findByExerciseIdAndPersonaOrderByPriorityAsc` + NULL fallback 2 쿼리
- 응답 4건 정도

**MySQL 만으로 풀 수 있는가**: ✅ 충분. 분기 4-A (클라 캐시) 가 *이미 서버 부담 최소화*

**부족함 증명**:
| 기준 | 증명 시도 | 판정 |
|---|---|---|
| (A) 정확성 | n/a — 단순 SELECT | ❌ |
| (B) 성능 | 측정 안 됨. 쿼리 2회 (PK index + uniqueKey index) 라 p95 <10ms 추정. 운동 시작 *세션당 1회* — 트래픽 무시 가능 | ❌ 미증명 |
| (C) 확장성 | 단일 운동 = 4 row 짜리 데이터. 캐싱 필요성 발생 시점 *원격* | ❌ 미증명 |

→ **Redis 가치 없음**. 클라 캐시 + DB 쿼리 1회/세션 으로 충분.

---

### 2.4 Refresh token 저장소

**현재 구현**: `refresh_token` MySQL 테이블 + `RefreshTokenRepository` (member_id PK)

**MySQL 만으로 풀 수 있는가**: ✅ 이미 그렇게 풀고 있음

**부족함 증명**: 호출 빈도 = *세션당 1~2회 (로그인 + 재발급)*. p95 < 5ms 예상. *측정 안 됐지만* 가설상 충분.

| 기준 | 판정 |
|---|---|
| (A) | ❌ |
| (B) | ❌ |
| (C) | ❌ |

→ **Redis 가치 없음**. *오히려* Redis 로 옮기면 휘발성 → 재시작 시 로그인 세션 전체 무효 (UX 악화).

---

### 2.5 Rate limiting

**현재 구현**: **없음**

**MySQL 만으로 풀 수 있는가**: ⚠️ 가능하나 비효율 (counter 컬럼 매 요청 UPDATE)

**부족함 증명**: *기능 자체가 미구현* 이므로 "MySQL 로 부족"이 아니라 **신규 기능 도입 결정** 의 문제.

→ **rate limit 가 *필요* 한지부터 결정**해야 함. 현재 트래픽 미미한 MVP 에서 rate limit 자체가 premature. 도입 시점이 되면 ConcurrentHashMap → Redis 격상은 자연 — 단 *지금* 결정할 사안 아님.

---

### 2.6 동시성·분산 락 (Redlock 등)

**현재 구현**: `Session.@Version` 낙관적 락 (`Session.java:65`) — `SessionService.completeSession` 의 3회 retry 패턴

**MySQL 만으로 풀 수 있는가**: ✅ 이미 그렇게 풀고 있음. 낙관적 락 + retry 가 *현재 충돌 시나리오 (AI 콜백 vs 타임아웃 스케줄러) 충분 처리*

**부족함 증명**:
| 기준 | 판정 |
|---|---|
| (A) 정확성 — 낙관적 락이 처리 못 하는 시나리오 있는가 | ❌ 미증명 (현재 시나리오는 모두 covered) |
| (B) 성능 — retry 비용이 SLO 위반? | ❌ 측정 안 됨 |
| (C) 확장성 — Spring 다중 인스턴스 시 분산 락 필요? | 단일 인스턴스 — ❌ |

→ **분산 락 = 다중 Spring 인스턴스 운영 시작 trigger 후 결정 사항**. 지금 도입 근거 없음.

---

### 2.7 캐싱 도입 (포폴 어필 v4 단계)

**현재 구현**: `@Cacheable` 사용처 0건, Caffeine 의존성 0건, Redis 의존성 0건

**`25-portfolio-strategy.md`** 의 진화기 트랙 C v4 가 *"Redis 캐싱 도입 (cache-aside + stampede 방지)"* 으로 설계됨 — *진화기 어필용*

**부족함 증명**:
| 기준 | 판정 |
|---|---|
| (A) | ❌ |
| (B) **baseline 측정** | ❌ 측정 안 됨. *진화기는 v1 baseline 측정 → v2 N+1 해결 → v3 인덱스 → v4 Redis 캐싱* 순서이므로 v1·v2·v3 측정 *전* 에 v4 도입은 진화기 자체 깨짐 |
| (C) | ❌ |

→ **포폴 어필 목적의 Redis 도입은 *v4 단계까지 도달했을 때만 의미 있음***. 지금 (v1 baseline 측정 전) 도입은 *순서가 뒤바뀐 어필* 이라 면접에서 들킴.

---

## 3. 종합 — 엄격한 증명 결과

| Use case | (A) 정확성 | (B) 성능 | (C) 확장성 | 현재 도입 가치 |
|---|:--:|:--:|:--:|---|
| 2.1 JwtBlacklist | ❌ | ❌ | ❌ | **약함** (MySQL persist 로 충분) |
| 2.2 AI session_state | ⚠️ (분기 D1 결정) | n/a | ⚠️ (분기 D 영역) | **분기 D 결정 사항** |
| 2.3 페르소나 캐시 | ❌ | ❌ | ❌ | **없음** |
| 2.4 Refresh token | ❌ | ❌ | ❌ | **없음** (오히려 악화) |
| 2.5 Rate limiting | n/a | n/a | n/a | **기능 자체 미도입 — 무관** |
| 2.6 분산 락 | ❌ | ❌ | ❌ | **없음** (낙관적 락 충분) |
| 2.7 캐싱 어필 v4 | ❌ | ❌ baseline 미측정 | ❌ | **순서 어긋남** |

**엄격한 결론**: 현재 MVP 시점에서 *MySQL 이 부족함* 을 엄격하게 증명 가능한 use case **0건**.

---

## 4. 재검토 trigger — 어떤 조건이 발생해야 도입?

다음 *중 하나라도* 발생 시 본 문서 재검토:

| Trigger | 영향 use case | 판정 신호 |
|---|---|---|
| **T1. 다중 Spring 인스턴스 운영 시작** | 2.1 JwtBlacklist, 2.6 분산 락 | 트래픽 증가로 horizontal scale 필요 — 베타 사용자 50+ 이후 |
| **T2. 분기 D 가 D1 → D2 격상** | 2.2 AI session_state | `ai-load-budget.md` §6.2 (b) 동시 사용자 N=5 시점 |
| **T3. baseline 측정 후 SLO 위반** | 2.3 페르소나 캐시, 2.7 어필 | 한 API 깊이 트랙 v1 측정 → v2 (N+1) v3 (인덱스) 적용 *후에도* p95 > 임계값 |
| **T4. JWT 보안 사고 (재시작 시 토큰 무효화 실패)** | 2.1 JwtBlacklist | 실제 사고 또는 보안 감사 지적 |
| **T5. Rate limit 기능 도입 결정** | 2.5 | 별도 결정. 단일 인스턴스면 ConcurrentHashMap 먼저, 다중 인스턴스면 Redis |

→ **MVP 단계 (시연 + 베타 50명 이하) 에선 트리거 미발생 가능성 높음**.

---

## 5. 포폴 어필 관점 — 별도 판단

사용자 메모리 [[user_career_target]] — *AWS·Redis·OAuth2·SQS* 단골 시그널. Redis 도입 이력 자체가 어필.

### 5.1 두 갈래의 어필 방식

| 방식 | 면접에서의 인상 |
|---|---|
| **A. "Redis 썼습니다"** — 도입 근거 빈약, 측정 없음 | ⚠️ "왜 썼나요?" → "캐싱이 좋아서요" 답하면 *들킴*. 시그널 약함 |
| **B. "MySQL 측정 → 부족 증명 → Redis 도입 → 측정 → 트레이드오프"** | ✅ *과정* 자체가 시니어 시그널. 강함 |

→ **A 보다 B 가 압도적 어필**. B 의 핵심은 *측정 + 사유 + 트레이드오프* 박제.

### 5.2 본 문서가 B 갈래에 기여하는 방식

본 문서 자체가 *Redis 도입 보류* 라는 *근거 있는 결정* 이라 어필 가능:

> "Redis 가 백엔드 채용 단골이라 도입 검토했으나, MVP 단계에서 MySQL 이 부족하다는 *엄격한 증명* 이 안 됐고 — JwtBlacklist 의 ConcurrentHashMap 은 단일 인스턴스에서 충분, 페르소나 템플릿은 클라 캐시 + 1회 쿼리로 충분, 분산 락은 낙관적 락 + retry 로 충분. *측정 데이터 확보 후* 진짜 부족함이 보이면 도입하는 게 시그널이라 판단해 보류했습니다."

→ **"안 쓴 이유" 가 명료한 게 *쓴 이유* 보다 강한 시그널** 케이스.

### 5.3 진화기 트랙 C 와의 정합

`25-portfolio-strategy.md` 의 v1→v7 진화기 가 *baseline 측정 → 인덱스 → Redis* 순서를 박아둠. 본 문서는 그 순서를 *지금 깨지 말 것* 을 박제. 측정 단계 (v1) 부터 시작 권장.

---

## 6. 추천 — 사용자 결정 대기

### 6.1 추천 A — **도입 보류 박제** ⭐

- 본 문서를 *결정 박제* 로 닫음 (✅ 마크)
- 재검토 trigger 5개 (§4) 와 함께 박제
- 다음 작업으로 *v1 baseline 측정* 으로 전환 (진화기 트랙 C 시작)
- 면접 답변 자료로 본 문서 활용

### 6.2 추천 B — 별도 검토 영역에서만 *제한적* 도입

- T2 trigger (분기 D) 만 우선 검토 — *AI 부하 측정 후 동시 사용자 N=2~5 한계 도달 시 D2 격상*
- 다른 use case (1, 3~7) 는 보류

### 6.3 추천 C — 어필 우선 도입 (비추)

- v4 단계 (진화기) 까지 기다리지 않고 *지금* 도입
- 위험: 면접에서 *근거 빈약함* 들킬 가능성. 시그널 약함

---

## 7. 결정 사항

> ✅ **추천 A 채택 / 결정자: 사용자 / 일자: 2026-05-25**
>
> - MVP 시점 *MySQL 부족함 엄격하게 미증명* — Redis 도입 보류
> - §4 의 trigger 5개 (T1~T5) 중 하나라도 발생 시 본 문서 재오픈
> - 다음 작업으로 *v1 baseline 측정* (진화기 트랙 C, `25-portfolio-strategy.md`) 전환
> - 본 문서를 면접 답변 자료로 활용 — *"왜 안 썼나"* 가 더 강한 시그널이라는 판단

---

## 관련 문서

- [`./ai-backend-coupling.md`](./ai-backend-coupling.md) §D — AI session_state 외부화 분기 (D1 채택)
- [`./ai-load-budget.md`](./ai-load-budget.md) §4·6 — 부하 측정 계획 및 분기 D 격상 trigger
- [`../tasks/25-portfolio-strategy.md`](../tasks/25-portfolio-strategy.md) — 진화기 트랙 C v1→v7 (Redis 는 v4)
- [`../REQUIREMENTS.md`](../REQUIREMENTS.md) — 현재 요구사항 (rate limit 등 미명시)
