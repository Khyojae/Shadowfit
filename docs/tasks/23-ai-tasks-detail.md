# AI Server 작업 상세 — AI-01 ~ AI-03

마지막 업데이트: 2026-05-24
출처: [`21-task-assignment.md`](./21-task-assignment.md) §3 의 항목별 풀이.
범위: 각 작업이 **어떤 파일을 / 무엇을 / 왜 / 어디까지** 만져야 하는지 + 현재 코드 상태 확인. 담당자(=ai-server 원작자)가 받아서 바로 시작할 수 있는 단위.

> **정책 알림** ([`feedback-minimize-python-changes`](../../../C:/Users/khjae/.claude/projects/E--init/memory/feedback_minimize_python_changes.md))
> ai-server 는 다른 사람 설계. 이 문서의 모든 항목은 **원작자가 진행하거나, 진행 전에 사용자 확인**. 우리(Spring 쪽 작업자)가 자의적으로 손대지 않음.
>
> **현재 상태**: 시연용 동작에 추가 필요한 작업 없음. 모든 항목은 [`project-squat-first`](../../../C:/Users/khjae/.claude/projects/E--init/memory/project_squat_first.md) 정책상 **새 운동 추가 결정 시점**까지 보류.

읽는 법:
- **현재 상태**: 코드를 실제로 확인한 결과 (📁 = 존재, ❌ = 부재, ⚠️ = 부분 구현)
- **만질 파일**: 신설/수정 대상 — 파일:라인 표기
- **완료 기준**: 무엇이 되면 끝났다고 볼 수 있는가
- **리스크/의존**: 시작 전에 결정 받아야 할 것

---

## AI-01 — `ExtractReferenceData` 실제 구현 (YouTube 다운로드 + MediaPipe 추출)

**우선**: ⚪ | **추정**: 6h | **상태**: 🟦 보류 | **담당**: 원작자 | **의존**: 새 운동 추가 결정

### 현재 상태
- 📁 proto 정의: `ai-server/app/proto/exercise.proto:42-52` — `ExtractRequest{exercise_id, youtube_url, extracted_poses[]}` / `ExtractResponse{success, exercise_id, extracted_poses[]}`
- ⚠️ 구현: `ai-server/app/grpc/exercise_servicer.py:124-139` — **stub**. 로그만 찍고 빈 배열 반환
- ❌ YouTube 다운로드 라이브러리 (`yt-dlp` / `pytube`) `requirements.txt` 에 **없음**
- ❌ YouTube 다운로드용 임시 디렉터리/캐시 정책 **없음**
- 📁 기존 기준 자세 생성기: `ai-server/app/core/reference_builder.py` — **업로드된 로컬 영상**에서 rep 세그먼트 추출 후 평균화 (YouTube 미지원)
- 📁 영상 처리: `ai-server/app/core/video_processor.py` — 로컬 파일 입력 받아 MediaPipe 좌표 추출
- 📁 호출 경로 (Spring 측): `service/Exercise/ExerciseAnalysisService.java:extractReferencePoses` — 04-15 4eb153b 에서 추가됨. **현재는 호출하면 AI 가 빈 배열 줌**

### 만질 파일
1. `ai-server/requirements.txt` — `yt-dlp` 추가 (`pytube`는 YouTube 차단 대응이 약함, `yt-dlp` 권장)
2. `ai-server/app/core/youtube_downloader.py` 신설
   - `download_to_tempfile(url: str) -> Path` — `yt-dlp` 로 mp4 저장, 호출자가 `finally` 로 삭제
   - 차단·존재하지 않는 URL 등 예외 정의 (`YoutubeDownloadError`)
   - 파일 크기 한도 (예: 200MB) — 무한 다운로드 방지
3. `ai-server/app/grpc/exercise_servicer.py:124-139` — `ExtractReferenceData` 메서드 실제 구현
   - `youtube_downloader.download_to_tempfile(request.youtube_url)`
   - `video_processor.analyze_video(temp_path)` → landmarks 시퀀스
   - `reference_builder.build_reference_sequence(landmarks, exercise_type)` → rep 평균 좌표
   - `ExtractResponse(success=True, extracted_poses=[...])` 반환
   - 실패 시 `success=False`, gRPC `INTERNAL` 또는 `INVALID_ARGUMENT` 상태 코드
4. `ai-server/Dockerfile` — `yt-dlp` 가 `ffmpeg` 필요할 수 있음 (`apt-get install -y ffmpeg`)
5. `ai-server/tests/test_extract_reference_data.py` 신설
   - 정상 YouTube URL (짧은 공개 영상 1개 고정), 잘못된 URL, 비공개 URL 케이스

### 완료 기준
- Spring 측 `extractReferencePoses` 호출 시 AI 가 실제 좌표 배열 반환
- 임시 mp4 파일이 응답 후 디스크에 남지 않음 (정상·실패 둘 다)
- 단위 테스트 통과

### 리스크/의존
- **YouTube ToS** — 다운로드 행위 자체가 ToS 회색지대. 시연/연구 용도 명시 필요
- **저작권** — 기준 영상 출처를 운영자가 직접 등록한 것만 허용하는 정책 권장
- **차단 대응** — YouTube 가 주기적으로 차단을 강화함 → `yt-dlp` 버전 핀 고정 + 정기 업데이트 필요
- **대용량 다운로드 시간** — 5분 영상도 30초+ 걸릴 수 있음. gRPC 타임아웃 (Spring 측 deadline) 검토 필요
- **운동별 분석 분기 의존**: `reference_builder.build_reference_sequence` 가 `exercise_type` 인자를 받아 운동별 rep 경계를 다르게 잡아야 함 → **AI-02 선행 필요**

---

## AI-02 — 런지·플랭크 분석기 추가

**우선**: ⚪ | **추정**: 운동당 4h+ | **상태**: 🟦 보류 | **담당**: 원작자 | **의존**: 새 운동 추가 결정

### 현재 상태
- 📁 스쿼트 분석기: `ai-server/app/core/squat_analyzer.py:236-330` — `StreamingSquatAnalyzer` 클래스
- 📁 운동 타입 인자: `StreamingSquatAnalyzer.__init__(exercise_type="squat", ...)` — 인자는 받지만 **사실상 스쿼트 전용**
- 📁 각도 정의: `ai-server/app/utils/constants.py` 의 `EXERCISE_ANGLES` dict — 스쿼트 항목만
- 📁 각도 추출: `ai-server/app/core/angle_calculator.py:25-31` — `extract_angles(landmarks, exercise_type)` 가 `EXERCISE_ANGLES.get(exercise_type)` 로 분기 (이미 일반화 일부 적용됨)
- 📁 phase 전환 로직: `squat_analyzer.py:91-100` — `knee_angle` 의 standing/bottom threshold 로 rep 카운트 (스쿼트 고유)
- ⚠️ 운동 ID → exercise_type 매핑: `exercise_servicer.py:69-70` — **"squat" 하드코딩**
- 📁 DTW/필터/감지기: `dtw_calculator.py`, `pose_filter.py`, `mediapipe_detector.py` — **운동 무관**, 그대로 재사용 가능

### 만질 파일
1. `ai-server/app/utils/constants.py` — `EXERCISE_ANGLES` 에 `"lunge"`, `"plank"` 추가
   - 런지: front knee angle, back knee angle, hip flexion
   - 플랭크: shoulder-hip-ankle 각도 (직선성 측정)
2. **분석기 분리 — 두 가지 접근**

   **선택지 A — 클래스 분리 (명시적, 추천)**
   - `ai-server/app/core/lunge_analyzer.py` 신설 — `StreamingLungeAnalyzer`
   - `ai-server/app/core/plank_analyzer.py` 신설 — `StreamingPlankAnalyzer`
   - `squat_analyzer.py` 그대로 둠
   - `exercise_servicer.py` 에서 `exercise_type` 으로 어떤 클래스를 쓸지 분기

   **선택지 B — 일반화 (중복 적음, 위험)**
   - `squat_analyzer.py` → `streaming_exercise_analyzer.py` rename
   - phase 전환 로직을 운동별 strategy 패턴으로 추출
   - 기존 단위 테스트 전부 영향 → 회귀 위험

3. `ai-server/app/grpc/exercise_servicer.py:69-70` — `exercise_type` 매핑 함수 구현
   - 입력: `request.exercise_id` (Spring 에서 보냄)
   - 출력: `"squat"` / `"lunge"` / `"plank"`
   - 매핑 출처: AI 측에 dict 하드코딩 (간단) **또는** Spring 측에서 `exercise_type` 문자열을 함께 보내도록 proto 확장 (B-안, 결합도 ↑)
4. `ai-server/tests/test_lunge_analyzer.py`, `test_plank_analyzer.py` 신설
   - 합성 landmarks 로 rep/hold 카운트 검증

### 완료 기준
- Spring 이 `exercise_id` = 런지/플랭크인 세션 시작 → AI 가 그에 맞는 분석기로 처리
- rep 카운트 / hold 시간 정확
- 기존 스쿼트 테스트 무회귀

### 리스크/의존
- **플랭크는 rep 카운트 개념이 안 맞음** — "rep" 대신 "hold 지속 시간(초)" 이 자연스러움. `PoseDataRequest` / `CompleteRequest` 의 의미 통일 필요 (예: `total_reps` 를 플랭크에서는 `hold_seconds` 의미로 재사용?) → **결합 인터페이스 합의 사항**
- **선택지 A vs B 결정** — A 가 위험 적지만 중복. 시연용은 A 권장
- **`exercise_id → exercise_type` 매핑 소스** — AI 하드코딩 (빠름) vs proto 확장 (결합 ↑) → proto 확장이 깔끔하나 양쪽 동기 비용 (현재 proto 동기 패턴 [`architecture/ai-backend-monthly-log.md`](../architecture/ai-backend-monthly-log.md) 참고)

---

## AI-03 — 운동 세트 자동 구분 분석

**우선**: ⚪ | **추정**: 4h | **상태**: 🟦 보류 | **담당**: 원작자 | **의존**: BE-09 와 협의 (proto / DB 양쪽)

### 현재 상태
- 📁 rep 카운트: `squat_analyzer.py:290` 의 `state.rep_count++` — **이미 구현**
- ❌ 세트 구분 로직 — **없음**. idle/rest period 감지 미구현
- 📁 세션 상태: `ai-server/app/grpc/session_state.py:32-50` — `rep_count`, `sync_rates[]`, `feedback_messages[]` 보유. **`set_count`, `completed_sets[]` 없음**
- ❌ proto 의 `PoseDataRequest`·`CompleteRequest` 에 set 관련 필드 **없음**
- 📁 motion threshold 후보: `pose_filter.py` — 좌표 평활화. idle 감지는 좌표 변화량 기반으로 별도 구현 필요

### 만질 파일
1. **proto** (BE-09 와 동시 진행 필수, 양쪽 동기)
   - `backend/src/main/proto/exercise.proto` + `ai-server/app/proto/exercise.proto`
     - `PoseDataRequest` 에 `set_index(5)`, `rep_index_in_set(6)` 추가
     - `CompleteRequest` 에 `int32 total_sets`, `repeated SetMetrics sets` 추가 (`SetMetrics{set_index, rep_count, avg_sync_rate, duration_sec}`)
2. `ai-server/app/grpc/session_state.py` — 필드 추가
   - `set_count: int = 1`
   - `completed_sets: list[SetMetrics] = []`
   - `last_motion_frame_index: int = 0` (idle 판정용)
   - `current_set_start_rep: int = 0`
3. `ai-server/app/core/squat_analyzer.py:278-293` — phase 전환 로직 확장
   - 새 상태 `"idle"` 추가
   - motion threshold (예: landmarks 변화량이 3초 동안 임계치 미만) → `idle` 진입
   - `idle` → `ready` 복귀 시 set 경계 판정 → `completed_sets.append(...)`, `set_count++`
4. `ai-server/app/grpc/spring_client.py:report_complete_analysis` — `total_sets`, `sets` 필드 채워서 전송
5. `ai-server/app/grpc/exercise_servicer.py` — `SavePoseDataBatch` 에서 `set_index`, `rep_index_in_set` 채워서 전송
6. `ai-server/tests/test_set_detection.py` 신설
   - 합성 시퀀스: rep 5개 → 5초 idle → rep 5개 → 세트 2개로 인식 검증

### 완료 기준
- 세션 종료 시 Spring 이 `total_sets`, `sets[]` 받음
- 각 PoseData 가 `set_index` 가지고 DB 저장됨
- 단위 테스트: idle 감지 정확도 (rep 사이 짧은 휴식은 같은 세트, 3초+ 휴식은 새 세트)

### 리스크/의존
- **세트 경계 정의가 모호** — 시간 기반 idle (3초+) vs 사용자 명시적 "다음 세트" 음성 명령 vs UI 버튼 → **시연용은 시간 기반이 자연스러움**
- **BE-09 (백엔드 set 컬럼) 동시 진행 필수** — proto 확장은 양쪽 동시. 한쪽만 가면 깨짐
- **플랭크 등 시간 기반 운동과 의미 충돌** — 플랭크의 "1세트" 는 hold 시간 1회. set 구분 알고리즘이 운동별로 달라야 함 → AI-02 와 협의
- **threshold 튜닝 비용** — motion threshold 값은 실측으로 잡아야 함 (실제 사용자 영상 필요)

---

## 권장 시작 순서 (보류 해제 시)

1. **AI-02 선행** — 분석기 분리 구조가 잡혀야 AI-01 의 `reference_builder` 가 운동별 분기를 가질 수 있고, AI-03 의 set 구분 알고리즘도 운동별 다르게 들어갈 수 있음
2. **AI-01** — YouTube 다운로드 + ExtractReferenceData 완성 (AI-02 의 `exercise_type` 분기를 활용)
3. **AI-03** — proto 확장 + BE-09 와 동시 진행. 세트 자동 구분

---

## 작업 시작 전 결정 받아야 할 항목

| 작업 | 결정 사항 | 추천 |
|------|---------|------|
| AI-01 | YouTube 다운로드 ToS / 저작권 정책 | 운영자가 등록한 URL 만 허용 |
| AI-01 | gRPC deadline (긴 영상 처리) | 60초 + 별도 큐로 비동기화 검토 |
| AI-02 | 분석기 분리(A) vs 일반화(B) | A (클래스 분리, 회귀 위험 낮음) |
| AI-02 | `exercise_id → exercise_type` 매핑 위치 | proto 에 `exercise_type` 필드 추가 (Spring 이 보냄) |
| AI-02 | 플랭크의 "rep" 의미 | `hold_seconds` 로 의미 재정의, proto 주석 명시 |
| AI-03 | 세트 경계 정의 | 시간 기반 idle 3초+ |
| AI-03 | BE-09 와 proto 동시 변경 일정 | 같은 PR/스프린트에 묶기 |

---

## 관련 문서
- [`21-task-assignment.md`](./21-task-assignment.md) — 원본 작업 분배표 (AI-01~03 의 요약 행)
- [`22-backend-tasks-detail.md`](./22-backend-tasks-detail.md) — BE-09 (세트 도입) 의 백엔드 측 풀이
- [`20-feature-roadmap.md`](./20-feature-roadmap.md) — PPT 요구사항 ↔ 코드 매핑
- [`../architecture/ai-backend-integration.md`](../architecture/ai-backend-integration.md) — 현재 결합 현황
- [`../architecture/ai-backend-monthly-log.md`](../architecture/ai-backend-monthly-log.md) — proto 양쪽 동기 패턴 (04-14 이후)
- `ai-server/docs/grpc_ai_server_design.md` — AI 측 설계 문서 (원작자 작성, e8e1b65)
