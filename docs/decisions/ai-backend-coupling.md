# Decision: AI ↔ Backend 결합 방식

상태: **OPEN (사용자 결정 대기)**
작성: 2026-05-23
배경 문서: [`docs/architecture/ai-backend-integration.md`](../architecture/ai-backend-integration.md)

---

## 1. 배경 / 문제

ShadowFit은 Spring(Java) 백엔드와 별도 팀이 작성한 Python AI 서버를 분리 운영한다. 현재는 gRPC 양방향 RPC + proto 파일 수동 동기화 + 내부 토큰 인증으로 묶여 있다 (현황 문서 참조).

다음 변경들이 예고돼 있어서 결합 방식을 한 번 정리해두지 않으면 같은 결정을 매 PR마다 즉흥적으로 내리게 된다:

- (가까운 미래) AI 콜백 신뢰성 더 끌어올리기 — 3회 재시도로는 부족한 상황 발생 가능
- (가까운 미래) 새 운동 종목(런지·플랭크 등) 추가 시 `exercise_type` 일반화 ([`project-squat-first`](../../../C:/Users/khjae/.claude/projects/E--init/memory/project_squat_first.md))
- (잠재) AI 서버 수평 확장 또는 GPU 인스턴스 분리
- (잠재) proto 중복 파일 동기화 자동화

**제약**: 사용자는 AI 서버 코드를 다른 사람이 작성한 영역으로 간주하여 변경을 최소화하고자 함 ([`feedback-minimize-python-changes`](../../../C:/Users/khjae/.claude/projects/E--init/memory/feedback_minimize_python_changes.md), 2026-05-23 재확인). 따라서 결합 변경은 **Spring 쪽만으로 가능한 안**을 디폴트로, AI 쪽 손대는 안은 명시적 정당화 필요.

---

## 2. 현재 보유 자원 · 제약

### 자원
- gRPC 기반 양방향 통신이 이미 동작 중 (proto 정의·인증 인터셉터·재시도 로직 완비)
- MySQL 하나로 트랜잭션·낙관적 락 일관성 보장 (Spring 측)
- Docker Compose 단일 노드 운영 — 컨테이너간 DNS와 내부 네트워크 분리 이미 정착
- 콜백 신뢰성 1차 강화 완료 (커밋 `c7657f1`: thread-local MediaPipe, sync 분석, 1s/3s 재시도)

### 제약
- **인력**: Java/Kotlin은 사용자가 직접 다룸. Python(AI)은 원작자가 따로 있고 사용자는 손대기를 꺼림.
- **언어 경계**: proto 외에는 코드 재사용 불가, 공통 라이브러리 없음.
- **인프라**: 단일 docker-compose. 메시지 큐(Kafka/Redis Streams 등) 없음. 영구 큐 도입은 운영 비용 증가.
- **수평 확장**: AI 세션 상태가 in-memory라 현재 1 인스턴스 가정. 늘리려면 AI 코드 손대야 함 (제약과 충돌).
- **스코프**: 지금은 스쿼트 한 종목만 ([`project-squat-first`](../../../C:/Users/khjae/.claude/projects/E--init/memory/project_squat_first.md)). 추상화 비용을 미리 지불할 이유 적음.
- **언어**: 한국어 단일 ([`project-korean-only`](../../../C:/Users/khjae/.claude/projects/E--init/memory/project_korean_only.md)). 메시지 다국어 분리는 불필요.

---

## 3. 결정해야 할 분기점

이 문서는 여러 결정을 한 번에 다루지 않고, 분기점별로 섹션을 나눠 사용자가 항목별로 답할 수 있게 한다.

### 분기 A. 콜백 신뢰성을 어디까지 올릴 것인가
### 분기 B. proto 중복 파일을 어떻게 다룰 것인가
### 분기 C. 새 운동 종목 추가 시 `exercise_type` 표현 방식
### 분기 D. AI 세션 상태 저장 위치
### 분기 E. 통신 프로토콜 자체 (gRPC 유지 vs 변경)

---

## 4. 분기 A: 콜백 신뢰성

**문제**: AI → Spring `CompleteAnalysis` 콜백이 3회 재시도 후 모두 실패하면 사용자의 세션 결과가 영구히 유실. 현재 ERROR 로그만 남음.

**보유 안전망 (현황)**:
- AI 콜백 3회 재시도 + 1s/3s 백오프 (커밋 `c7657f1`, `spring_client.report_complete_analysis`)
- `SessionTimeoutScheduler` 가 1분 주기로 `IN_PROGRESS` + 타임아웃 초과 세션을 `FAILED` 로 떨어뜨림 (`startTime + expectedDurationMinutes + 30분` 기준)
- 낙관적 락(`@Version`) + 멱등성 가드로 콜백 중복/지연 도착 시 데이터 일관성 보장

즉 **세션이 "사라지지는" 않음** — 늦게라도 `FAILED` 로 닫힌다. 문제는 *"AI가 분석은 끝냈는데 콜백 전송이 영원히 실패한 경우"* → 사용자에겐 `FAILED` 로만 보이고 실제 결과는 AI 메모리에서만 사라짐.

| 선택지 | 한 줄 요약 | AI 변경 | Spring 변경 | 인프라 | 구현 비용 | 리스크 |
|--------|----------|--------|------------|--------|---------|-------|
| **A1. 현상 유지** | 3회 재시도 + 로그 + 스케줄러 안전망 | 없음 | 없음 | 없음 | 0 | 콜백 영구 실패 시 결과 유실, 운영자 인지 늦음 |
| **A6. 운영 알람만 추가** | ERROR 로그 → Slack/이메일 알림. Spring에 `RestTemplate` 1~2개 메서드 + webhook URL env 추가 | 없음 | 알람 헬퍼 1개 | webhook URL 1개 | 0~1 | 알람만으로 자동 복구는 안 됨, 수동 개입 필요 |
| **A2. Spring reconciliation 잡** | 타임아웃 초과 + AI 헬스 OK 세션을 주기적으로 AI에 재조회 | `GetSessionStatus` RPC 신설 필요 (현재 proto의 `GetFinalPoseData` 는 호출자 없음 — 그걸 살릴 수도) | 잡 + 큐 신설 | 없음 | 중 | AI 변경 동반 — proto 추가 또는 기존 RPC 활용 |
| **A5. 재시도 횟수·간격만 늘리기** | 3→10회, 지수 백오프, 최종 실패 시 보존 큐는 없음 | 작음 (`spring_client.py` 수치만) | 없음 | 없음 | 0~1 | 일시 장애 ↑, 장시간 장애에는 여전히 유실 |
| **A3. AI 디스크 큐 (SQLite/파일)** | 실패 콜백을 파일로 저장 후 백그라운드 워커가 재발송 | 큼 (큐·워커 신설) | 없음 | 없음 | 중~상 | AI 변경량 큼 |
| **A4. Outbox + 메시지 큐 (Redis Streams 등)** | AI가 큐에 publish, Spring이 consume | 콜백 경로 전면 변경 | consumer 신설 | 큐 컴포넌트 추가 | 상 | 인프라·AI 모두 큰 변경 |

**추천 (단계적)**:
1. **배포 전까지: 보류** (2026-05-23 정정) — 알람의 가치는 *"운영 중인 시스템에서 유실을 조기 발견"*. 아직 배포 전이라 실패가 발생할 환경 자체가 없고 받을 운영자도 없음. **A6 은 배포 직전·직후 1~2일 작업으로 묶어두는 것이 합리적.** 미리 빌드해두면 webhook URL 만 운영용으로 바꿔 끼우면 즉시 작동.
2. **배포 직전 결정**: A6 도입. 그때 4-1/4-2 sub-분기 답.
3. **A6 으로 알람으로 실제 빈도가 파악된 뒤**:
   - 사고가 거의 없으면 → A1+A6 으로 유지
   - 가끔 있고 수동 복구 가능 빈도면 → A5 (재시도 횟수 증가) 추가
   - 자주 있거나 자동 복구가 필요해지면 → A2 (reconciliation 잡)
4. **A3/A4 는 마지막 카드** — AI 변경량 크고 인프라 추가 비용 큼. 위 단계로 부족할 때만.

---

### 4-1. A6 sub-분기: 알람 채널

| 선택지 | 의존성 | 설정 비용 | 운영 비용 | 장점 | 단점 |
|--------|-------|---------|---------|------|------|
| **A6-CH-1. Slack Incoming Webhook** | Slack 워크스페이스 | 5분 (앱 생성→URL 발급) | 무료 | 즉시성 ↑, 채널별 분리, 무료 | Slack 안 쓰면 무의미, 외부 SaaS 의존 |
| **A6-CH-2. SMTP 이메일** | SMTP 서버 (Gmail SMTP 등) | 중 (앱 비밀번호·보내는 주소) | 무료~소액 | 외부 SaaS 의존 X, 보존 길음 | 즉시성 ↓ (메일 확인 안 함 가능), 스팸 필터 |
| **A6-CH-3. 둘 다** | 위 둘 다 | 합산 | 합산 | 채널 다양화, 한쪽 다운 시 백업 | 코드·env 약간 늘어남 |
| **A6-CH-4. 외부 모니터링 SaaS (Sentry / Better Stack 등)** | SaaS 가입 | 중~고 | 유료 가능성 | 메트릭·대시보드까지 한 번에 | 학습 곡선, 비용 발생 |
| **A6-CH-5. 보류 (구현 안 함)** | — | 0 | — | 결정 미룸 | A6 자체가 멈춤 |

**추천**: **A6-CH-1 (Slack 웹훅)** — 5분 설정, 무료, 즉시성 가장 높음. 추후 다른 채널 필요해지면 `AlertService` 인터페이스 뒤에 구현체만 추가하면 됨.

### 4-2. A6 sub-분기: 감지 지점 (어디서 알람을 쏠 것인가)

콜백 실패는 **AI 측에서 발생** (`spring_client.report_complete_analysis` 가 3회 실패 후 ERROR 로그). Spring 은 이 사실을 직접 못 봄. 따라서 Spring 이 알람을 쏘려면 *간접 신호* 를 사용해야 함.

| 선택지 | 어디서 감지 | AI 변경 | 정확도 | 지연 |
|--------|-----------|--------|-------|------|
| **A6-DP-1. `SessionTimeoutScheduler` 가 FAILED 처리 시** | Spring 측, 타임아웃 도달 세션 | 없음 | 중 (타임아웃 = 콜백 실패 OR AI 작업 미완 — 구분 X) | `expectedDurationMinutes + 30분` |
| **A6-DP-2. `SessionService.completeSession` 의 OptimisticLock 재시도 한도 초과 시** | Spring 측, DB 동시성 이상 | 없음 | 높음 (실제 충돌 발생) | 즉시 |
| **A6-DP-3. `ExerciseGrpcService.completeAnalysis` 예외 발생 시** | Spring 측, 콜백 처리 중 예외 | 없음 | 높음 (콜백 도착 후 처리 실패) | 즉시 |
| **A6-DP-4. AI 측에서 콜백 최종 실패 시 별도 알람 직접 발송** | AI 측, `spring_client.py` | 있음 (작음) | 가장 정확 | 즉시 |
| **A6-DP-5. 위 1+2+3 조합** | Spring 측 3 곳 | 없음 | 합산 | 합산 |

**추천**: **A6-DP-5 (Spring 측 3곳 조합)** — AI 무변경 + 가장 넓은 커버리지. 단점은 DP-1 의 정확도 한계(타임아웃이 콜백 실패 때문인지 진짜 AI 처리 지연인지 구분 X) 인데, 알람 본문에 *"AI 헬스체크 결과"* 도 같이 실으면 운영자가 즉시 판단 가능.

DP-4 (AI 측 직접 알람) 는 정확도는 가장 높지만 [`feedback-minimize-python-changes`](../../../C:/Users/khjae/.claude/projects/E--init/memory/feedback_minimize_python_changes.md) 위배라 보류.

**미결 질문**:
- 콜백 실패가 실제로 관찰된 적 있나? 빈도? → 현재는 알람이 없어 *"관찰됐는지 자체를 모름"*. A6 이 이 질문에 답할 도구.
- 알람 채널은 무엇? (Slack 웹훅 / 이메일 / 둘 다 / 다른 것)
- "데이터 유실 1건" 의 사용자 영향 수준 — 통계 왜곡인지 단순 세션 1개 누락인지? 이게 자동 복구(A2) 가 필요한지의 기준.

---

## 5. 분기 B: proto 중복 파일

**문제**: `backend/src/main/proto/exercise.proto`와 `ai-server/app/proto/exercise.proto`가 동일한데 수동 동기화. 한쪽만 바꾸면 런타임 직렬화 오류로만 발견.

| 선택지 | 한 줄 요약 | AI 변경 | Spring 변경 | 구현 비용 | 리스크 |
|--------|----------|--------|------------|---------|-------|
| **B1. 현상 유지** | 손으로 동기화, PR 리뷰에서 확인 | 없음 | 없음 | 0 | 동기화 누락 가능 |
| **B2. 단일 소스 + 빌드 시 복사** | 한쪽을 master로, 빌드 스크립트가 다른 쪽으로 복사 | 빌드 스크립트 1줄 | 빌드 스크립트 1줄 | 저 | master 위치 정해야 함 |
| **B3. 별도 `proto/` 디렉터리** | 저장소 루트에 `proto/exercise.proto` 두고 양쪽이 참조 | proto 경로 변경 (코드 생성 설정만) | proto 경로 변경 (코드 생성 설정만) | 저~중 | 양쪽 빌드 설정 한 번 손대야 함 |
| **B4. proto 별도 저장소 + 버전 태그** | 독립 repo, 양쪽이 git submodule/패키지로 의존 | 빌드 설정 | 빌드 설정 | 중 | 운영 복잡도 ↑ |
| **B5. buf 같은 도구 도입** | `buf` lint·breaking check 추가 | CI 추가 | CI 추가 | 중 | CI 러닝 비용 |

**추천**: **B3 (루트 `proto/` 단일 소스)** — 한 번만 손대면 이후 자동 동기. AI 빌드 설정은 import path만 바꾸는 정도라 "AI 동작 코드 변경"은 아니라고 본다. 하지만 buf까지는 과잉.

**구체적인 변경 면**:
- B3 진행 시 AI 측은 `ai-server/Dockerfile` 의 `python -m grpc_tools.protoc -I./app/proto …` 줄을 `-I../proto …` 로, `ai-server/app/proto/exercise.proto` 는 삭제(또는 symlink).
- Spring 측은 `backend/build.gradle` 의 `protobuf.protoc.srcDir` 을 루트 `proto/` 로 지정.
- 즉 양쪽 다 **빌드 설정 1줄씩** + **proto 파일 위치 이동** 만. 양쪽 동작 코드(`spring_client.py`, `exercise_servicer.py`, `ExerciseAnalysisService.java` 등) 는 무변경.

**미결 질문**:
- 사용자가 위 "빌드 설정 1줄 + proto 위치 이동" 을 *"AI 변경"* 으로 간주하는가? ([`feedback-minimize-python-changes`](../../../C:/Users/khjae/.claude/projects/E--init/memory/feedback_minimize_python_changes.md) 의 보호 대상에 해당하는지 명시 필요)
- 다음 proto 변경 PR 이 들어올 시점이 알려진 게 있나? 그때 함께 정리하면 비용 최소화.

---

## 5-α. 분기 F: 자세 데이터 영속화 정책

> **정정 (2026-05-23)**: 본 분기는 처음 작성 시 "AI 측 트리거 없음" 이라는 잘못된 전제로 출발했습니다. 실제 코드 확인 결과 **F1 (Push) 이 이미 구현돼 동작 중**. 트리거 위치가 예상과 달라서 (`squat_analyzer.py` 가 아니라 `app/api/endpoints/pose.py:116`) 갭 분석이 놓쳤음. 본 분기는 결정 완료로 닫힘.

**문제 (당시 추정)**: 운동 중 프레임별 자세 데이터를 Spring DB(`pose_data` 테이블)에 어떻게 넣을 것인가.

**현황 (실제 코드 기준, 정정)**:
- `SavePoseDataBatch` (AI → Spring push): **이미 동작 중**. 트리거는 `app/api/endpoints/pose.py:116` — 프론트가 `POST /pose` 를 프레임 단위로 호출 → AI 가 `StreamingSquatAnalyzer.process_frame` 로 rep 감지 → rep 완성 시 `spring_client.report_pose_data_batch` 자동 호출
- `GetFinalPoseData` (Spring → AI pull): proto만 있고 양쪽 호출자 없음 — F1 으로 push 되니까 안전망으로만 의미 가능, 또는 폐기 후보
- `CompleteAnalysis` 로는 집계값만 전송 (`total_reps`, `avg/max/min_sync_rate`, `calories`) — 프레임 데이터는 위 push 경로로 들어감

**프론트 의존성**: 위 push 경로는 **프론트가 `POST /pose` 를 호출해야 시작됨**. 프론트 코드가 이 호출을 안 하고 있으면 `pose_data` 테이블이 비어 있게 됨. 이 책임은 AI/Spring 결합 외부(프론트).

| 선택지 | 의미 | AI 변경 | Spring 변경 | DB 부하 | 데이터 유실 위험 | 리포트 상세도 |
|--------|------|--------|------------|--------|--------------|-----------|
| **F1. Push (rep 단위)** | `squat_analyzer` rep 완성 시 `spring_client.report_pose_data_batch` 한 줄 호출. 종료 시 `CompleteAnalysis` 로 집계 따로 | 한 줄 (트리거) | 없음 | 분산 (rep 단위 INSERT) | 콜백 실패 시 해당 rep 손실 (다른 rep 은 영향 없음) | 높음 (rep별 시계열) |
| **F2. Pull (종료 시 한 번에)** | AI 가 메모리에 보존, 종료 후 Spring 이 `GetFinalPoseData` 로 한 번에 받음 | 응답 핸들러 채우기 (중간) | 호출 코드 추가 | 끝에 집중 (대량 INSERT) | AI 컨테이너 죽으면 전체 손실 | 높음 |
| **F3. Push + Pull 안전망** | F1 으로 진행 + 종료 시 `GetFinalPoseData` 로 한 번 더 검증 | F1 + 응답 핸들러 | F1 + 종료 후 검증 호출 | F1 + 끝에 검증 | 가장 낮음 | 가장 높음 |
| **F4. 영속화 안 함** | 둘 다 폐기. 집계값만 DB. 프레임 데이터는 AI 메모리에서만 사용 후 소멸 | 둘 다 폐기 (정리) | 둘 다 폐기 (정리) | 0 | — | 낮음 (집계만, 시계열 X) |

**F1 의 구체 변경 면** (가장 작은 옵션):
- 위치: `ai-server/app/core/squat_analyzer.py` 의 rep 완성 감지 후 콜백
- 변경: `spring_client.report_pose_data_batch(session_id, [rep_data_list])` 한 줄
- proto·Spring 수신부·AI 콜백 함수 무변경 (이미 다 있음)
- 사실상 **"마지막 밸브 하나 열기"** 수준의 변경

**추천**: **F1 (Push)** — 사용자가 직접 제안한 흐름. 변경 크기 최소(한 줄), 분산된 INSERT 로 부하 안정, rep별 유실은 격리됨(전부 사라지지 않음). F3 의 추가 안전망은 운영하면서 콜백 유실이 실제로 관찰될 때 추가.

**미결 질문**:
- F1 의 "AI 한 줄 추가" 가 [`feedback-minimize-python-changes`](../../../C:/Users/khjae/.claude/projects/E--init/memory/feedback_minimize_python_changes.md) 의 동작 코드 변경 보호 대상에 해당하는가? 사용자가 직접 제안한 흐름이라 예외인가?
- rep "완성" 의 정의 — squat 의 경우 down→up 한 사이클? 분석기 측 코드 흐름 확인 필요.
- 한 rep 의 데이터 크기 — 프레임 수 × `joint_coordinates(JSON)`. DB INSERT 단위 비용 검토.

---

## 5-β. 분기 H: 카메라 프레임 송신 경로 (프론트 → AI `POST /pose`)

**문제**: 프론트가 운동 중 카메라 프레임을 AI 의 `POST /pose` 로 보내야 자세 분석이 시작됨. 그런데 AI 의 HTTP 8000 은 **외부 노출 안 됨** (`docker-compose.yml` 의 `expose` 만, 커밋 c7657f1) — 프론트가 직접 부를 수 없다.

**현황**:
- AI `POST /pose` 는 무인증 — 외부 노출하면 임의 데이터 주입 가능 (그래서 expose 만 둔 것)
- 백엔드는 같은 Docker 네트워크 안이라 AI 8000 호출 가능
- 프론트는 외부 클라이언트 — 백엔드 8080 만 호출 가능

| 선택지 | 의미 | AI 변경 | Spring 변경 | 보안 | 지연 |
|--------|------|--------|------------|------|------|
| **H1. 백엔드 프록시** | 프론트 → 백엔드 `/exercises/sessions/{id}/frame` → 백엔드가 WebClient 로 AI `POST /pose` 전달 | 없음 | controller 1개 + service 1개 (~70줄) | ✅ 외부 노출 유지 차단 | 한 홉 추가 (~수십 ms, base64 페이로드라 무시 가능) |
| **H2. AI 8000 외부 노출 + 인증 추가** | docker-compose `ports` 복귀 + AI HTTP 에 토큰 검증 미들웨어 추가 | 큼 (HTTP auth 미들웨어 신설) | 없음 | ⚠️ 노출 후퇴 — 토큰만으로 충분한가? | 한 홉 (가장 짧음) |
| **H3. 백엔드가 자체 MediaPipe** | Java/Kotlin MediaPipe 라이브러리로 백엔드에서 직접 분석 | 무관 (AI 서버 불필요해질 수도) | 매우 큼 | ✅ | 백엔드 부하 ↑ |
| **H4. WebSocket / gRPC streaming** | 프론트 ↔ 백엔드 양방향 스트리밍, 백엔드 ↔ AI 도 streaming | 큼 | 큼 | ✅ | 가장 낮음 (스트리밍) |

**구체적인 변경 면 (H1)**:
- **Spring**:
  - `ExercisesController` 에 `POST /exercises/sessions/{sessionId}/frame` 추가
  - `dto/exercises/PoseFrameRequestDto` 신설 — `{ image: String(base64), timestampSec: Double, exerciseType: String }`
  - `service/Exercise/PoseFrameProxyService` (가칭) — `WebClient` 로 `http://shadowfit-ai:8000/pose` 호출 후 응답 그대로 반환
- **AI**: 무변경. 기존 `POST /pose` 가 그대로 받는다.
- **docker-compose**: 무변경.

**추천**: ~~**H1 (백엔드 프록시)**~~ → **H2 (프론트 직결) 채택** (2026-05-24, §11 결정 로그 참조).

H1 잠정 추천이었으나 사용자 의도(PPT 아키텍처 = 프론트 → AI 직결, 한 홉 빠른 실시간성) 가 명확해 H2 로 확정. 보안 후퇴 우려는 **AI 측 인증 미들웨어 추가**로 해소. 자세한 사유는 §11 결정 로그.

**미결 질문** (H2 확정 후에도 유효):
- 프레임 전송 빈도? — 매 프레임? 0.1초당 1회? 송신 빈도가 지연·AI 부하 결정
- base64 페이로드 크기 — 640×480 JPEG 압축 가정 시 한 프레임 ~30~50KB. 초당 N회면 대역폭 N×40KB/s
- 프레임 송신 실패 시 정책 — 재시도? 스킵? (AI 는 rep 완성 시 콜백이라 일부 프레임 누락은 큰 문제 아님)

---

## 5-γ. 분기 I: 인증 토큰 흐름 (H2 채택의 부속 분기)

**문제**: H2 채택으로 프론트가 AI `POST /pose` 직접 호출 → AI 측 인증 미들웨어가 어떤 토큰을 검증할 것인가. 백엔드의 JWT 와 어떻게 연결?

**선택지**:

| 선택지 | 의미 | 비용 | 보안 |
|--------|------|------|------|
| **I1. `INTERNAL_API_TOKEN` 정적 공유** ⭐ 잠정 | 이미 c52f677 에서 백엔드·AI 양쪽에 환경변수 주입됨. 프론트에도 같은 토큰 노출. AI 미들웨어는 `Authorization: Bearer ${INTERNAL_API_TOKEN}` 검증 | 0 (인프라 작업만) | ⚠️ 사용자 격리 X, 토큰 탈취 시 누구나 AI 호출 가능 |
| I2. 백엔드가 세션 단위 단기 토큰 발급 | `POST /exercises/sessions` 응답에 AI 호출용 토큰 포함, 만료 짧게 (5분?). AI 미들웨어가 검증 (백엔드에 verify 호출 or JWT signature 검증) | 백엔드 +3h (`AiTokenService`), AI 측 토큰 검증 로직 | ✅ 사용자별 격리, 만료 시 자동 무효 |
| I3. JWT 공유 | 백엔드/AI 같은 JWT secret. AI 가 JWT signature 검증 | AI 측 JWT 라이브러리 추가 | ✅ |

**추천**: **I1 잠정 + 운영 단계에서 I2 로 전환 검토**.

사유:
- 시연·베타 단계엔 사용자 격리 필요성 낮음 (지인 5~10명 대상)
- I1 은 이미 있는 자원 활용이라 시연까지 빠르게 갈 수 있음
- 운영 단계에서 외부 사용자 늘면 I2 로 전환 (토큰 탈취 시 영향 격리)

**미결 질문**:
- I1 정적 토큰을 프론트 환경변수에 박을지, 백엔드 응답으로 매 세션 줄지
- I2 로 전환 시점은? (베타 종료 후? 외부 사용자 수 N 이상?)

---

## 6. 분기 C: 새 운동 종목 시 `exercise_type`

**문제**: 현재 AI는 `exercise_type="squat"` 하드코딩(`exercise_servicer.py:70`), `_analyzers = {"squat": ...}`(`pose.py:28-30`). proto에 `exercise_type` 필드 없음. 두 번째 운동이 들어올 때 어떻게 표현할지.

이 분기는 [`project-squat-first`](../../../C:/Users/khjae/.claude/projects/E--init/memory/project_squat_first.md) 결정에 따라 **두 번째 운동이 실제로 분석기를 갖고 오는 시점까지 미뤄둠**. 그때 다시 평가.

선택지만 미리 적어둠:

- **C1.** proto에 `exercise_type` 필드 추가, 한글-영문 매핑은 Spring에서
- **C2.** proto는 `exercise_id`(int64)만 보내고, AI가 자체 DB/설정으로 분석기 매핑
- **C3.** proto에 enum `ExerciseType` 정의

**현재 결정**: 보류. 두 번째 운동 PR과 같이 진행.

---

## 7. 분기 D: AI 세션 상태 저장 위치

**문제**: AI `SessionState`가 in-memory dict. AI 컨테이너 재시작 시 진행 중 세션 전부 유실. 수평 확장 불가.

| 선택지 | AI 변경 | Spring 변경 | 인프라 | 구현 비용 | 리스크 |
|--------|--------|------------|--------|---------|-------|
| **D1. 현상 유지** | 없음 | 없음 | 없음 | 0 | 재시작 시 유실, 단일 인스턴스 한정 |
| **D2. Redis로 외부화** | 큼 | 없음 | Redis 추가 | 중 | AI 큰 변경 |
| **D3. MySQL에 AI 세션 테이블 추가** | 큼 | 없음 (또는 읽기용 뷰) | 없음 | 중~상 | AI 변경량 큼, AI가 DB 직접 접근하는 새 결합 |
| **D4. 진행 중 상태도 Spring으로 매 N초 푸시** | 중 (콜백 추가) | endpoint 추가 | 없음 | 중 | proto 변경 필요, AI 변경 |

**추천**: **D1 (현상 유지)**. 단일 인스턴스 운영 가정이 깨질 때 재평가. AI 변경 비용이 모두 큰 영역.

**미결 질문**:
- AI 컨테이너 재시작 빈도? 무중단 배포 요구사항이 있는가?
- 동시 활성 세션 수가 단일 인스턴스 한도(메모리/CPU) 안에 들어오는가?

---

## 8. 분기 E: 통신 프로토콜 (gRPC 유지 vs 변경)

**문제**: gRPC가 정착돼 있지만, 콜백을 위해 Spring이 gRPC 서버도 띄워야 하는 양방향 구조. REST + Webhook으로 단순화하거나 큐로 전환하는 안도 이론상 존재.

| 선택지 | 한 줄 요약 | AI 변경 | Spring 변경 | 인프라 | 리스크 |
|--------|----------|--------|------------|--------|-------|
| **E1. 현재 gRPC 유지** | 양방향 unary RPC | 없음 | 없음 | 없음 | 양쪽이 server+client 둘 다 구현 (이미 함) |
| **E2. Spring → AI: REST, AI → Spring: Webhook(REST)** | 표준 HTTP만 사용 | 큼 (gRPC 전면 교체) | 큼 | 없음 | 큰 마이그레이션, 타입 안전성 ↓ |
| **E3. 메시지 큐 양방향** | Kafka/Redis Streams | 매우 큼 | 매우 큼 | 큐 추가 | 인프라 복잡도, 학습 곡선 |

**추천**: **E1 유지**. gRPC가 이미 동작하고 타입 안전성·성능 다 이점. 변경 비용이 이득 대비 너무 큼.

---

## 9. 종합 권장 (디폴트 선택지)

사용자 별도 지시 없으면 다음 디폴트로 진행:

| 분기 | 디폴트 | 이유 |
|------|--------|------|
| A. 콜백 신뢰성 | **A6 (운영 알람 추가)** | AI 무변경. 가장 시급한 부분은 "실패가 났는지조차 모름" 이라 알람으로 가시성 먼저 확보 |
| B. proto 동기화 | **B3 (루트 `proto/`)** 또는 **B1 유지** | B3가 장기적 안전, 단 사용자가 "빌드 설정 1줄 변경 = AI 변경" 으로 보는지에 따라 결정 |
| C. `exercise_type` | **보류** | [`project-squat-first`](../../../C:/Users/khjae/.claude/projects/E--init/memory/project_squat_first.md) |
| D. AI 세션 저장 | **D1 (in-memory 유지)** | AI 변경 비용 과다 |
| F. 자세 데이터 영속화 | **F1 (Push, rep 단위)** ✅ 이미 구현됨 | `pose.py:116` 에 트리거 존재 확인 (2026-05-23 정정) |
| E. 통신 프로토콜 | **E1 (gRPC 유지)** | 이미 안정화 |

전체 방향: **현재 gRPC 양방향 결합을 유지하면서, Spring 쪽 운영·모니터링·재시도만 강화. AI는 buildscript/proto 경로 외에는 손대지 않는다.**

---

## 10. 미결 질문 (사용자 답변 필요)

각 질문 옆 `→` 는 그 답이 어느 결정을 흔드는지 표시.

1. AI 콜백 실패가 실제로 운영 중 관찰됐는가? 빈도와 영향은? → **분기 A** (현재 알람이 없어 관찰 가능성 자체가 없음 — A6 도입의 동기)
2. 알람 채널은 무엇? Slack 웹훅 / 이메일 / 기타? → **분기 A** A6 의 구현 방식
3. "데이터 유실 1건" 의 사용자 영향 수준은? 통계 왜곡인지 단순 세션 1개 누락인지? → **분기 A** A2 자동 복구가 필요한지의 기준
4. proto 빌드 설정 변경 1줄 + proto 파일 위치 이동을 "AI 변경" 으로 보는가? → **분기 B** B3 진행 가능 여부
5. 다음 proto 변경 PR 이 들어올 시점이 알려진 게 있나? → **분기 B** B3 와 묶어서 진행할지 별건으로 진행할지
6. 동시 활성 세션 수 상한 추정치는? AI 무중단 배포 요구가 있는가? → **분기 D**
7. 위 분기들 중 가장 먼저 손대고 싶은 게 어느 것인가? (혹은 전부 보류?) → 우선순위

---

## 11. 결정 로그

- **2026-05-23**: 분기 A 의 A6 (운영 알람 추가) 채택 검토 → **배포 전이라 보류**. 운영 환경에서 실패가 발생할 수 없는 시점이므로 알람의 가치 낮음. 배포 직전·직후 1~2일 작업으로 묶어 진행 예정. sub-분기(채널 / 감지 지점)도 그때 결정.
- **2026-05-23**: 분기 F 의 F1 (자세 데이터 Push) 채택 시도 → 코드 재확인 결과 **이미 구현·동작 중** (`ai-server/app/api/endpoints/pose.py:116` 의 `spring_client.report_pose_data_batch` 호출). 추가 코드 변경 불필요. 단 push 경로가 시작되려면 **프론트가 `POST /pose` 를 프레임 단위로 호출** 해야 한다는 전제 명시. 분기 닫힘.
- **2026-05-23**: 분기 G (`GetFinalPoseData` 처리) → **G1 (폐기) 채택**. 이유: `SavePoseDataBatch` push 가 같은 역할을 이미 함, AI 측 핸들러도 미구현(`UNIMPLEMENTED`) 상태였음. 양쪽 `exercise.proto` 에서 RPC 1개 + `SessionRequest` / `PoseDataList` 메시지 삭제. 동작 코드 무변경. 다음 빌드 시 `_pb2.py` 자동 갱신.
- **2026-05-24**: 분기 H → **H2 (프론트 → AI 직결) 채택**. 사유:
  - 사용자 의도가 PPT 아키텍처 그대로 (실시간 영상 분석 = 프론트 직결)
  - 성능: 한 홉 절감 (~5~30ms), 5fps 미만에선 무의미하나 10fps 이상에선 큐잉 차이
  - 백엔드 부하 분산: base64 페이로드(~40KB/프레임 × 사용자 수) 가 백엔드 메모리/GC 거치지 않음
  - 보안 후퇴(외부 노출) 는 **AI 측 인증 미들웨어 추가**로 해소
  - `c7657f1` 의 `expose` 차단은 H 미결 + AI 무인증 상태의 잠정 조치였음 — `ports` 복귀
  - 영향: BE-01 (백엔드 프록시) 폐기, BE-10·11·12 신설 (헬스체크·콜백 검증·Outbox 패턴), 새 sub-분기 I 신설
- **2026-05-24**: 분기 I (인증 토큰 흐름) → **I1 (`INTERNAL_API_TOKEN` 정적 공유) 잠정 채택**. 운영 단계에서 I2 (세션 단위 단기 토큰) 전환 검토. 사유: 시연·베타까지 사용자 격리 필요성 낮음, 이미 c52f677 에서 양쪽 환경변수 주입됨.
