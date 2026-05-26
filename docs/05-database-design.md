# 데이터베이스 설계 가이드

> 이 문서는 설계 의도와 핵심 스키마를 정리합니다. **운영 중인 실제 스키마는 `mysql/schema.sql` 이 단일 진실 원천**이며, 본 문서와 다를 수 있는 부분은 마지막 "코드 동기 메모" 절에서 명시합니다.

## ERD 개요
회의록에서 정의된 DB 활용 3단계 로드맵에 따른 설계입니다.
- **1단계**: 현재 상태 기록
- **2단계**: 과거 히스토리 관리 및 추이 분석
- **3단계**: 미래 예측 (누적 데이터 기반)

## 테이블 설계

### users (사용자) — 실제 스키마 기준
```sql
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    username VARCHAR(50) UNIQUE NOT NULL,         -- 닉네임 역할
    sex ENUM('MALE', 'FEMALE', 'NONE') DEFAULT 'NONE',
    role VARCHAR(20) DEFAULT 'ROLE_USER',         -- Spring Security 권한
    profile_image_url VARCHAR(500),
    height DECIMAL(5,1),
    weight DECIMAL(5,1),
    workout_level VARCHAR(20),
    selected_persona ENUM('BEGINNER', 'ADVANCED', 'DIET', 'REHAB') NOT NULL DEFAULT 'BEGINNER',
    preferred_url VARCHAR(500),                   -- 사용자 기본 기준 영상
    onboarding_completed BOOLEAN DEFAULT FALSE,
    tts_enabled BOOLEAN NOT NULL DEFAULT TRUE,    -- TTS 사용 여부 (2026-05 추가)
    tts_speed DECIMAL(3,1) NOT NULL DEFAULT 1.0,  -- TTS 속도 0.5~2.0 (2026-05 추가)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

> 컬럼명 차이: 회의록 초안의 `nickname` 은 코드상 `username`. 컬럼 추가 이력은 [`architecture/ai-backend-monthly-log.md`](./architecture/ai-backend-monthly-log.md) 의 04-25/05-09 절 참조.

### exercises (운동 종목 마스터)
```sql
CREATE TABLE exercises (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,                          -- 스쿼트, 데드리프트, 턱걸이
    category ENUM('LOWER', 'BACK', 'UPPER', 'CORE', 'FULL') NOT NULL,
    description TEXT,
    preferred_url VARCHAR(500),                          -- 기본 레퍼런스 영상 (코드상 컬럼명)
    target_joints JSON,                                  -- 분석 대상 관절 목록
    sync_threshold_beginner DECIMAL(5,2) DEFAULT 60.00,  -- 헬린이 기준
    sync_threshold_advanced DECIMAL(5,2) DEFAULT 85.00,  -- 헬창 기준
    expected_duration_minutes INT DEFAULT 15,            -- 세션 타임아웃 산정용 (2026-05 추가, 커밋 136f0e6)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### exercise_references (운동별 기준 좌표) — 2026-04 추가
```sql
CREATE TABLE exercise_references (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    exercise_id BIGINT NOT NULL,
    timestamp_sec DECIMAL(10,3) NOT NULL,
    joint_coordinates JSON NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (exercise_id) REFERENCES exercises(id) ON DELETE CASCADE,
    INDEX idx_exercise_ref_id (exercise_id)
);
```
관리자가 유튜브 URL을 등록하면 AI 서버에서 추출한 기준 포즈 시퀀스가 이 테이블에 저장된다. 운동 세션 시작 시 Spring 이 여기서 조회해 gRPC `StartAnalysis` 의 `reference_poses` 필드로 AI에 전달. (커밋 4eb153b)

### exercise_sessions (운동 세션)
```sql
CREATE TABLE exercise_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,                       -- 코드상 컬럼명 (users.id FK)
    exercise_id BIGINT NOT NULL,
    reference_source VARCHAR(500),                   -- 사용된 기준 영상 (로컬 경로 or YouTube URL)
    start_time DATETIME NOT NULL,
    end_time DATETIME,
    total_reps INT DEFAULT 0,
    avg_sync_rate DECIMAL(5,2),
    max_sync_rate DECIMAL(5,2),
    min_sync_rate DECIMAL(5,2),
    calories_burned DECIMAL(7,2),
    difficulty_level INT DEFAULT 1,
    status ENUM('IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'FAILED') DEFAULT 'IN_PROGRESS',
    version BIGINT NOT NULL DEFAULT 0,               -- JPA @Version (낙관적 락, 2026-05 추가)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (member_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (exercise_id) REFERENCES exercises(id)
);
```

**동시성**: `version` 컬럼은 AI 완료 콜백과 `SessionTimeoutScheduler` 가 동시에 같은 세션을 갱신할 때 충돌을 감지하기 위한 낙관적 락. `SessionService.completeSession` 에서 `OptimisticLockingFailureException` 발생 시 3회 재시도. (커밋 136f0e6, [`15-session-timeout-guide.md`](./15-session-timeout-guide.md) 참조)
**상태 추가**: 기존 3개 상태 외에 `FAILED` 추가 — 스케줄러가 타임아웃된 세션을 떨어뜨릴 때 사용.

### pose_data (자세 데이터 - 1초당 평균값 저장)
```sql
CREATE TABLE pose_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    timestamp_sec DECIMAL(10,3) NOT NULL, -- 소수점 타임스탬프 (코드상 DECIMAL)
    joint_coordinates JSON NOT NULL,      -- 관절 좌표 평균값 (33개 포인트)
    sync_rate DECIMAL(5,2) NOT NULL,      -- 해당 초의 싱크로율
    is_correct BOOLEAN DEFAULT TRUE,
    feedback_message VARCHAR(500),        -- 실시간 피드백 메시지 (한국어, [project-korean-only])
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES exercise_sessions(id) ON DELETE CASCADE,
    INDEX idx_session_timestamp (session_id, timestamp_sec)
);
```

### daily_logs (달력 일지)
```sql
CREATE TABLE daily_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    log_date DATE NOT NULL,
    memo TEXT,                            -- 사용자 메모
    total_exercise_time INT DEFAULT 0,    -- 당일 총 운동 시간 (분)
    total_calories DECIMAL(7,2) DEFAULT 0,
    mood ENUM('GREAT', 'GOOD', 'NORMAL', 'BAD', 'TERRIBLE'),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    UNIQUE KEY uk_user_date (user_id, log_date)
);
```

### reports (운동 보고서)
```sql
CREATE TABLE reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    report_type ENUM('SESSION', 'WEEKLY', 'MONTHLY') DEFAULT 'SESSION',
    summary TEXT,                          -- GPT 생성 피드백 요약
    detailed_analysis JSON,               -- 상세 분석 데이터
    improvement_tips TEXT,                 -- 개선 포인트
    comparison_with_previous JSON,        -- 이전 기록 대비 변화량
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (session_id) REFERENCES exercise_sessions(id)
);
```

### body_records (신체 변화 기록 - 3단계용)
```sql
CREATE TABLE body_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    record_date DATE NOT NULL,
    weight DECIMAL(5,1),
    body_fat_percentage DECIMAL(4,1),
    muscle_mass DECIMAL(5,1),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (member_id) REFERENCES users(id),
    INDEX idx_member_date (member_id, record_date)
);
```

### exercise_feedback_templates (운동별 TTS 피드백 멘트) — 2026-05 추가
```sql
CREATE TABLE exercise_feedback_templates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    exercise_id BIGINT NOT NULL,
    feedback_type VARCHAR(30) NOT NULL,    -- KNEE_OVER, BACK_BEND, GOOD_FORM, REP_COUNT 등
    message VARCHAR(200) NOT NULL,         -- 한국어 멘트 (예: "무릎이 발끝을 넘었습니다")
    priority INT NOT NULL DEFAULT 100,     -- 낮을수록 우선
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (exercise_id) REFERENCES exercises(id) ON DELETE CASCADE,
    UNIQUE KEY uk_exercise_feedback (exercise_id, feedback_type)
);
```
세션 시작 시 클라이언트가 `GET /exercises/{exerciseId}/feedback-templates` 로 받아 device TTS 로 재생. 다국어 분리 컬럼 없음 ([`project-korean-only`](../../C:/Users/khjae/.claude/projects/E--init/memory/project_korean_only.md)).

### session_feedback_logs (세션별 TTS 발화 이벤트 로그) — 2026-05 추가
```sql
CREATE TABLE session_feedback_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    feedback_type VARCHAR(30) NOT NULL,
    sync_rate_at_trigger DECIMAL(5,2),     -- 발화 시점 싱크로율 스냅샷
    occurred_at DATETIME NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES exercise_sessions(id) ON DELETE CASCADE,
    INDEX idx_session_feedback (session_id, occurred_at)
);
```
운동 중 device TTS 가 실제 발화한 시점을 AI 가 gRPC `ExerciseService.ReportFeedbackBatch` (인증: `Authorization: Bearer`) 로 송신. BT-SET (분기 2.A.BT) 모델 — 세트 경계마다 mini-batch + 세션 종료 시 final batch. 매 rep 실시간 호출 금지. 멱등성: `(session_id, occurred_at, feedback_type)` uniqueKey + `INSERT IGNORE` 로 retry 안전.

## joint_coordinates JSON 구조 예시
MediaPipe의 33개 관절 포인트에 대한 1초 평균 좌표:
```json
{
  "landmarks": [
    {"id": 0, "name": "nose", "x": 0.51, "y": 0.32, "z": -0.12, "visibility": 0.99},
    {"id": 11, "name": "left_shoulder", "x": 0.62, "y": 0.45, "z": -0.08, "visibility": 0.95},
    {"id": 12, "name": "right_shoulder", "x": 0.40, "y": 0.44, "z": -0.09, "visibility": 0.96},
    {"id": 23, "name": "left_hip", "x": 0.58, "y": 0.72, "z": 0.01, "visibility": 0.92},
    {"id": 24, "name": "right_hip", "x": 0.44, "y": 0.71, "z": 0.02, "visibility": 0.91},
    {"id": 25, "name": "left_knee", "x": 0.57, "y": 0.88, "z": 0.05, "visibility": 0.88},
    {"id": 26, "name": "right_knee", "x": 0.45, "y": 0.87, "z": 0.06, "visibility": 0.87}
  ]
}
```

## 데이터 저장 전략
- **실시간 분석 데이터**: 모든 프레임이 아닌 **1초당 평균값**만 저장 (회의록 결정사항)
- **좌표 데이터**: JSON 타입으로 유연하게 저장
- **인덱스**: 세션별 시계열 조회를 위해 `(session_id, timestamp_sec)` 복합 인덱스 적용
- **인코딩**: 전체 charset `utf8mb4` 강제 (한국어 피드백 메시지 깨짐 방지, 커밋 0fe056e)

---

## 코드 동기 메모 (회의록 초안 ↔ 실제 schema.sql 차이)

| 회의록 초안 | 실제 코드 | 사유 |
|------------|---------|------|
| `users.nickname` | `users.username` | 코드 작성 시 명명 |
| `users.persona` | `users.selected_persona` | Spring 엔티티 필드명과 정렬 |
| `exercises.reference_video_url` | `exercises.preferred_url` | 코드 작성 시 명명 |
| `exercise_sessions.user_id` | `exercise_sessions.member_id` | Member 엔티티 명칭 |
| `body_records.user_id` | `body_records.member_id` | 위와 동일 |
| `pose_data.timestamp_sec INT` | `DECIMAL(10,3)` | 소수점 타임스탬프 대응 |
| `status` 3개 enum | 4개 (`FAILED` 추가) | 스케줄러 타임아웃 처리 |
| (없음) | `users.tts_enabled`, `tts_speed` | 2026-05 TTS 설정 |
| (없음) | `exercises.expected_duration_minutes` | 2026-05 타임아웃 산정 |
| (없음) | `exercise_sessions.version` | 2026-05 낙관적 락 |
| (없음) | `exercise_references` 테이블 | 2026-04 기준 좌표 저장 |
| (없음) | `exercise_feedback_templates`, `session_feedback_logs` | 2026-05 TTS 피드백 |

스키마 변경 이력은 [`architecture/ai-backend-monthly-log.md`](./architecture/ai-backend-monthly-log.md) 와 함께 참조.
