# Decision: AI 서버 부하 버짓 / 베이스라인 측정

상태: **OPEN — 측정 미실시 (배포 전 1회 측정 후 확정 예정)**
작성: 2026-05-25
배경: TTS 작업이 AI 분류·batch 송신을 추가하면서 "AI 서버 부하가 터지나" 라는 우려 제기 (2026-05-25 사용자 질문). 사실 부하의 99% 는 *기존 시스템* 에서 발생 — TTS 와 별개로 정리해두지 않으면 같은 우려가 매 작업마다 반복됨.
연관: [`./tts-design.md`](./tts-design.md) 분기 7, [`./ai-backend-coupling.md`](./ai-backend-coupling.md) 분기 D·H2

---

## 1. 배경 / 문제

AI(FastAPI) 한 인스턴스가 다음을 모두 처리한다:

1. **매 프레임**: base64 디코드 → OpenCV BGR→RGB → MediaPipe 추론 → 각도 추출 → smoothing → rep 감지 (state machine)
2. **rep 완성 시**: DTW 기반 sync_rate 계산 → Spring 으로 PoseData batch gRPC 콜백 (동기 blocking)
3. **(TTS 작업 후 신규)**: 8종 결함 분류 (분기 1-B) → priority tie-break (분기 3-A) → 판정 이벤트 누적 (분기 2-A) → 세트 경계·세션 종료 시 gRPC `ExerciseService.ReportFeedbackBatch` 호출 (BT-SET, 분기 2.A.BT)

`pose.py:42` 의 `detect_pose` 가 동기 blocking 으로 FastAPI threadpool 에서 실행됨 ([`pose.py:46-48`](../../ai-server/app/api/endpoints/pose.py) 주석 명시). 동시 N 사용자 = N 동시 추론.

**질문**: 이 구성에서 어느 단계가 부하 병목이며, TTS 작업이 그 위에 얼마나 더 얹는가? 어디서 터지나?

---

## 2. 현재 자원·설정

| 항목 | 값 | 위치 |
|------|-----|------|
| DTW window | 10 (Sakoe-Chiba band) | [`config.py:14`](../../ai-server/app/config.py) `DTW_WINDOW_SIZE` |
| 분석 FPS 권장값 | 10 | [`config.py:18`](../../ai-server/app/config.py) `VIDEO_PROCESS_FPS` |
| 카메라 최대 FPS | 30 | [`config.py:17`](../../ai-server/app/config.py) `VIDEO_MAX_FPS` |
| 프론트 → AI 직결 결정 | 분기 H2 채택 (2026-05-24) | [`ai-backend-coupling.md`](./ai-backend-coupling.md) §H |
| AI 인스턴스 수 | 1 (수평 확장 미지원 — in-memory `session_state`) | [`session_state.py`](../../ai-server/app/grpc/session_state.py) |
| MediaPipe detector | thread-local 싱글톤 | 커밋 c7657f1 |
| sync_rate 알고리즘 | DTW per-joint (관절 4~6개) + 정규화 | [`dtw_calculator.py:10-31`](../../ai-server/app/core/dtw_calculator.py) |

---

## 3. 부하 단계별 분해

빈도·체감 비용·TTS 기여 여부를 한 표로:

| 단계 | 빈도 (10fps · 5분 세션 가정) | 단건 비용 (체감) | 세션 누적 | TTS 기여? |
|------|:--:|:--:|:--:|:--:|
| base64 디코드 + BGR→RGB | 3,000회 | 하 (~1ms) | ~3s | — |
| **MediaPipe 추론** | 3,000회 | **상 (~20~50ms CPU, GPU 면 ~5ms)** | **~1~3분 CPU 누적** | — |
| 각도 추출 + EMA smoothing | 3,000회 | 무시 (~0.1ms) | ~0.3s | — |
| rep 감지 state machine | 3,000회 | 무시 | ~0.1s | — |
| **DTW sync_rate** (window=10, 관절 4~6개) | rep 당 1회 (≈75회) | **중 (~5~20ms, rep 프레임 수 비례)** | **~1s** | — |
| Spring gRPC PoseData batch (동기) | rep 당 1회 (≈75회) | 중 (RTT ~5~20ms) | ~1s | — |
| **8종 분류 (각도 4개 임계값 비교)** | rep 당 1회 (≈75회) | **무시 (~0.1ms)** | **<0.01s** | ✅ |
| priority tie-break | rep 당 1회 (≈75회) | 무시 (O(4)) | <0.01s | ✅ |
| 판정 이벤트 누적 (list.append) | rep 당 1회 | 무시 | <0.01s | ✅ |
| 세션 종료 batch POST | 세션당 1회 | 중 (RTT 1회) | <0.05s | ✅ |

**결론 (분해상)**: MediaPipe 가 **CPU 시간의 95%+** 를 점유. DTW + gRPC 가 나머지 5% 수준. **TTS 작업 신규 항목 전체 합이 세션당 누적 0.1초 미만** — MediaPipe 1프레임 분(20~50ms) 의 ~2~5배에 불과. 실질 부하 영향 **무시 가능**.

---

## 4. 부하 위험 시나리오

TTS 와 별개로, *기존 시스템* 의 부하가 터질 시나리오는 다음 세 가지. 우선순위 순.

### 4.1 매 프레임 FPS 가 권장값(10) 보다 높을 때

- 클라가 throttle 없이 30fps 로 POST 하면 MediaPipe 부하 3배. CPU 100% 도달 가능
- `VIDEO_PROCESS_FPS=10` 은 *영상 전처리* 설정으로 실시간 POST 빈도와는 별개 — **클라 측 throttle 정책이 코드로 강제돼 있는지 미확인**
- 위험도: **상**. 측정 1순위

### 4.2 동시 사용자 N 명

- AI 1 인스턴스 + 동기 blocking + FastAPI threadpool
- threadpool default = `min(32, os.cpu_count() + 4)` — CPU 4 코어면 8 동시. 그러나 *각 worker 가 MediaPipe* 라 CPU 가 동시성 한계
- `session_state` in-memory 라 수평 확장 불가 ([`ai-backend-coupling.md`](./ai-backend-coupling.md) 분기 D)
- 위험도: **상**. 다중 사용자 시연 전엔 잠재

### 4.3 sync_rate 계산 비용 (rep 프레임 수 증가 시)

- DTW window 가 10 으로 제한돼 O(n×10) 인 점은 안심 요인 — n=100 이어도 1,000 step
- 단 관절 수만큼 반복 (4~6 회). 한 rep n=200 프레임이면 n × 10 × 6 = 12,000 step
- 위험도: **중**. 측정 후 확정

### 4.4 (TTS 와 무관하나 짚어둠) Spring gRPC 콜백 blocking

- AI worker thread 가 Spring 응답을 동기로 기다리는 동안 다음 프레임 처리 지연
- 위험도: **중**. Spring 측 응답 지연이 AI 부하로 전이

---

## 5. 측정 계획 (배포 전 1회)

목표: 위 4가지 시나리오별 마지노선 수치 확정. TTS 작업 PR 머지 *후*, 단일 사용자 5분 세션 베이스라인 + 동시성 stress 1회.

| 측정 항목 | 도구 | 합격 기준 (잠정) |
|----------|------|----------------|
| 단일 사용자 5분 세션 AI CPU 평균·peak | `docker stats` 또는 `psutil` 로깅 | 평균 < 60%, peak < 90% (1 vCPU 기준) |
| 단계별 wall time 분포 | `pose.py` 에 `time.perf_counter()` 임시 삽입 → MediaPipe / DTW / gRPC / 분류 분포 로그 | MediaPipe 가 95%+ 점유하면 정상 — TTS 가 1%↑ 면 이상 |
| HTTP /pose p50·p95·p99 latency | `pose.py` 응답 시간 로그 | p95 < 100ms (10fps 라면 100ms 가 다음 프레임 도착 시점) |
| 동시 2·3·5 사용자 stress | `locust` 또는 간이 스크립트로 동시 세션 N개 | N=2 까지 p95 < 200ms, N=5 면 격상 분기 트리거 |
| 5분 세션 종료 시점 메모리 누수 | RSS 추이 | 세션 종료 후 ±10% 안 복귀 |

**진행 방법**: 23-ai-tasks-detail.md 에 "AI 부하 베이스라인 측정" 작업 항목 신설. 측정 결과는 이 문서 §7 에 추가.

---

## 6. 측정 결과별 대응 옵션 (단계 격상)

측정 결과가 합격 기준 미달이면 다음 순서로 대응. 비용 작은 것부터.

### 6.1 우선순위 1 — 클라 frame throttle 강제

| 옵션 | 변경 위치 | 비용 |
|------|---------|------|
| (a) 클라 setInterval 10fps 고정 | RN 카메라 캡처 루프 | 작음 |
| (b) AI 측 rate limiting (token bucket) | `pose.py` 미들웨어 | 중간 |
| (c) MediaPipe 추론 결과를 클라에서 수행, AI 는 landmark 만 받음 | 큰 변경 — 분기 H2 와는 다른 방향 | 큼 |

추천: **(a) 부터** — 클라 1줄. 그래도 부족하면 (b).

### 6.2 우선순위 2 — 동시 사용자 한계 도달 시

| 옵션 | 비고 |
|------|------|
| (a) MediaPipe GPU 사용 (CUDA) | 인프라 변경. 단일 노드에선 어려움 |
| (b) AI 수평 확장 + 세션 sticky routing | [`ai-backend-coupling.md`](./ai-backend-coupling.md) 분기 D 결정 필요 (`session_state` 외부화) |
| (c) MediaPipe 모델 경량화 (lite vs full) | 정확도 trade off |
| (d) 동시 사용자 N 명 제한 (큐잉) | 운영 정책. 단기 |

추천: 시연 단계까진 **(d)** 로 운영, 정식 출시 시 **(b)** 검토.

### 6.3 우선순위 3 — Spring 콜백 blocking 완화

| 옵션 | 비고 |
|------|------|
| (a) `report_pose_data_batch` 를 FastAPI `BackgroundTasks` 로 async 처리 | 작음. 단 실패 시 retry 추적 손실 |
| (b) AI 내부 큐 → 별 worker | 큼. 분기 A3 와 연결 ([`ai-backend-coupling.md`](./ai-backend-coupling.md)) |

추천: **(a) 부터** — 실패 추적은 분기 A 의 알람으로 보완.

### 6.4 우선순위 4 — DTW 비용

| 옵션 | 비고 |
|------|------|
| (a) rep 프레임 sampling (n>50 면 down-sample) | 작음. 정확도 trade off 측정 필요 |
| (b) DTW window 축소 (10 → 5) | `DTW_WINDOW_SIZE` 상수 1줄 |

추천: 측정에서 DTW 가 단계별 분포 5%↑ 면 (b) → (a) 순.

---

## 7. 측정 결과 기록 (미실시)

| 일자 | 시나리오 | 결과 | 조치 |
|------|---------|------|------|
| TBD | 단일 사용자 5분 베이스라인 | 미실시 | — |
| TBD | 동시 2 사용자 | 미실시 | — |
| TBD | 동시 5 사용자 | 미실시 | — |

---

## 8. TTS 작업의 부하 기여도 (별 항목으로 명시)

[`./tts-design.md`](./tts-design.md) 분기 1·2·3·7 채택안의 부하 추가 합산:

| 신규 단계 | 빈도 | 단건 비용 | 세션 누적 |
|----------|:--:|:--:|:--:|
| 8종 분류 (각도 4 임계값) | rep 당 1회 | < 0.1ms | < 10ms |
| priority tie-break | rep 당 1회 | 무시 | 무시 |
| 판정 이벤트 list.append | rep 당 1회 | 무시 | 무시 |
| PoseResponse `feedback_type` 필드 직렬화 | rep 당 1회 | 무시 | 무시 |
| 세트 경계·세션 종료 batch (`gRPC ReportFeedbackBatch`) | 세트당 1회 (~3~5/세션) | RTT ~10ms (gRPC, protobuf) | ~30~50ms |

**합산**: 세션당 **< 50ms** 추가. MediaPipe 1 프레임 분(20~50ms) 의 1~2.5배. 베이스라인 대비 **노이즈 수준**.

→ 결론: **TTS 작업 자체로 부하가 터지는 일은 없음**. 부하 우려가 현실화되면 그것은 *기존 시스템* 의 §4.1·4.2 시나리오임. 이 문서가 그 분리를 명문화.

---

## 향후 재검토 트리거

| 사건 | 다시 봐야 할 부분 |
|------|----------------|
| 측정에서 단일 사용자 CPU > 90% | §6.1 클라 throttle 강제 즉시 |
| 동시 사용자 시연 계획 (베타·MVP 발표) | §5 stress 측정 + §6.2 옵션 결정 |
| Spring 응답 p95 > 50ms | §6.3 (a) async 화 즉시 |
| 운동 종목 추가 (런지·플랭크) — sync_rate 관절 수 ↑ | §4.3 DTW 재측정 |
| GPU 인스턴스 도입 가능성 | §6.2 (a) 옵션 살아남 |

---

## 결정 로그

- **2026-05-25**: TTS 작업으로 인한 AI 부하 우려에서 출발해 *기존 시스템 부하* 를 독립 분석 문서로 분리. 측정 미실시 — 23-ai-tasks-detail.md 에 측정 작업 신설 예정. 측정 후 §7 갱신.
- TTS 작업 자체는 부하 위험 없음 (§8) — tts-design.md 의 분기 1·2·3·7 진행 보류 사유로 사용 불가.

---

## 관련 문서

- [`./tts-design.md`](./tts-design.md) — TTS 분기 결정 (이 문서가 §8 에서 부하 기여도 보장)
- [`./ai-backend-coupling.md`](./ai-backend-coupling.md) — 분기 D (세션 상태 외부화), 분기 H2 (프론트 직결), 분기 A (콜백 신뢰성)
- [`../tasks/23-ai-tasks-detail.md`](../tasks/23-ai-tasks-detail.md) — 측정 작업 신설 대상
- [`../../ai-server/app/api/endpoints/pose.py`](../../ai-server/app/api/endpoints/pose.py) — 부하 측정 instrumentation 대상
- [`../../ai-server/app/core/dtw_calculator.py`](../../ai-server/app/core/dtw_calculator.py) — DTW 비용 측정 대상
