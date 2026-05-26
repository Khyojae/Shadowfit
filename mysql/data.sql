-- 인코딩 강제 (한글 깨짐 방지). 클라이언트 charset이 latin1 이어도 utf8mb4 로 협상.
SET NAMES utf8mb4;

-- 1. 기존 데이터 및 테이블 정리
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS body_records, reports, daily_logs, pose_data, session_feedback_logs, exercise_feedback_templates, exercise_sessions, exercise_references, exercises, refresh_token, users;
SET FOREIGN_KEY_CHECKS = 1;

-- 2. 사용자 테이블 (자바 Member 엔티티와 1:1 매칭)
CREATE TABLE users (
                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                       email VARCHAR(100) UNIQUE NOT NULL,
                       password VARCHAR(1000) NOT NULL,
                       username VARCHAR(50) UNIQUE NOT NULL, -- nullable = false 대응
                       role VARCHAR(20) NOT NULL,           -- Enum (USER, ADMIN)
                       selected_persona VARCHAR(10) NOT NULL DEFAULT 'BEGINNER',
                       preferred_url VARCHAR(500),          -- preferredUrl -> preferred_url (언더바)
                       height DECIMAL(5,1),
                       weight DECIMAL(5,1),
                       workout_level VARCHAR(20),
                       onboarding_completed BOOLEAN NOT NULL DEFAULT FALSE,
                       sex VARCHAR(10),
                       tts_enabled BOOLEAN NOT NULL DEFAULT TRUE,
                       tts_speed DECIMAL(3,1) NOT NULL DEFAULT 1.0,
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. 운동 테이블 (자바 Exercise 엔티티와 1:1 매칭)
CREATE TABLE exercises (
                           id BIGINT AUTO_INCREMENT PRIMARY KEY,
                           name VARCHAR(100) NOT NULL,
                           category VARCHAR(20) NOT NULL,       -- LOWER, CORE 등
                           description TEXT,
                           preferred_url VARCHAR(500),          -- Preferredurl -> preferred_url (언더바)
                           target_joints JSON,
                           sync_threshold_beginner DECIMAL(5,2) DEFAULT 60.00,
                           sync_threshold_advanced DECIMAL(5,2) DEFAULT 85.00,
                           expected_duration_minutes INT DEFAULT 15,
                           created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS exercise_sessions (
                                                 id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                 member_id BIGINT NOT NULL,
                                                 exercise_id BIGINT NOT NULL,
                                                 reference_source VARCHAR(500),
    start_time DATETIME NOT NULL,
    end_time DATETIME,
    total_reps INT DEFAULT 0,
    avg_sync_rate DECIMAL(5,2),
    max_sync_rate DECIMAL(5,2),
    min_sync_rate DECIMAL(5,2),
    calories_burned DECIMAL(7,2),
    difficulty_level INT DEFAULT 1,
    status VARCHAR(20) DEFAULT 'IN_PROGRESS',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (member_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (exercise_id) REFERENCES exercises(id)
    );

CREATE TABLE reports (
                         id BIGINT AUTO_INCREMENT PRIMARY KEY, -- 자동 증가 추가
                         session_id BIGINT,
                         member_id BIGINT,
                         report_type VARCHAR(20),
                         summary TEXT,
                         improvement_tips TEXT,
                         created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                         FOREIGN KEY (session_id) REFERENCES exercise_sessions(id),
                         FOREIGN KEY (member_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS refresh_token (
                                             member_id BIGINT PRIMARY KEY,
                                             token VARCHAR(255) NOT NULL,
    FOREIGN KEY (member_id) REFERENCES users(id) ON DELETE CASCADE
    );

CREATE TABLE IF NOT EXISTS exercise_feedback_templates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    exercise_id BIGINT NOT NULL,
    feedback_type VARCHAR(30) NOT NULL,
    persona VARCHAR(10) NULL,
    message VARCHAR(200) NOT NULL,
    priority INT NOT NULL DEFAULT 100,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (exercise_id) REFERENCES exercises(id) ON DELETE CASCADE,
    UNIQUE KEY uk_exercise_feedback_persona (exercise_id, feedback_type, persona)
);

CREATE TABLE IF NOT EXISTS session_feedback_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    feedback_type VARCHAR(30) NOT NULL,
    sync_rate_at_trigger DECIMAL(5,2),
    occurred_at DATETIME NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES exercise_sessions(id) ON DELETE CASCADE,
    INDEX idx_session_feedback (session_id, occurred_at),
    UNIQUE KEY uk_session_event (session_id, occurred_at, feedback_type)
);

-- 3. 데이터 삽입 시작
SET FOREIGN_KEY_CHECKS = 0;

-- 1. 유저 데이터 (ID 1번 확실히 생성)
INSERT INTO users (email, password, username, role, onboarding_completed, preferred_url)
VALUES ('test@test.com', '$2a$10$.mpvpjYHKGukSTvbCukWNusFWU/lHUBCmHjp3Un2mz6qjrOg9z/LC', '효재', 'USER', TRUE,
        'https://www.youtube.com/watch?v=q6hBSSis_60');

-- 2. 운동 종목 데이터 (REPLACE 사용으로 에러 방지)
REPLACE INTO exercises (id, name, category, preferred_url, created_at)
VALUES (1, '스쿼트', 'LOWER', 'https://www.youtube.com/watch?v=q6hBSSis_60', NOW());

REPLACE INTO exercises (id, name, category, preferred_url, created_at)
VALUES (2, '런지', 'LOWER', 'https://www.youtube.com/watch?v=U4s4mEQ5ovM', NOW());

REPLACE INTO exercises (id, name, category, preferred_url, created_at)
VALUES (3, '플랭크', 'CORE', 'https://www.youtube.com/watch?v=ASdvN_XEl_c', NOW());

-- 3. 운동 세션 데이터 (4월 데이터)
REPLACE INTO exercise_sessions (id, member_id, exercise_id, start_time, end_time, avg_sync_rate, total_reps, calories_burned, status, created_at) VALUES
(601, 1, 1, '2026-04-01 09:00:00', '2026-04-01 09:30:00', 75.5, 30, 150, 'COMPLETED', NOW()),
(602, 1, 2, '2026-04-03 18:00:00', '2026-04-03 18:40:00', 82.0, 40, 210, 'COMPLETED', NOW()),
(603, 1, 1, '2026-04-05 10:00:00', '2026-04-05 10:20:00', 88.5, 20, 100, 'COMPLETED', NOW()),
(617, 1, 1, '2026-04-25 09:00:00', '2026-04-25 09:20:00', 92.5, 20, 100, 'COMPLETED', NOW()),
(618, 1, 2, '2026-04-25 14:00:00', '2026-04-25 14:40:00', 88.0, 40, 190, 'COMPLETED', NOW()),
(619, 1, 3, '2026-04-25 20:00:00', '2026-04-25 20:30:00', 95.0, 30, 140, 'COMPLETED', NOW());

-- 3-A. 피드백 템플릿 시드 데이터 (운동별 자세 피드백 멘트)
-- 스쿼트 (id=1) — 4 결함 × 4 페르소나 = 16 row (12-persona-difficulty.md 톤 가이드)
INSERT INTO exercise_feedback_templates (exercise_id, feedback_type, persona, message, priority) VALUES
-- KNEE_OUT (무릎이 발끝보다 나감)
(1, 'KNEE_OUT', 'BEGINNER', '무릎이 발끝을 넘었어요. 살짝 뒤로 빼면 완벽해요', 10),
(1, 'KNEE_OUT', 'ADVANCED', '무릎이 발끝 전방으로 벗어남. 발목 가동범위 조정 필요', 10),
(1, 'KNEE_OUT', 'DIET',     '무릎 정렬 교정하면 자세가 안정되어 효율이 올라가요', 10),
(1, 'KNEE_OUT', 'REHAB',    '무릎이 안전 범위를 벗어났습니다. 천천히 자세를 조정해주세요', 10),
-- KNEE_IN (무릎이 안쪽으로 모임)
(1, 'KNEE_IN',  'BEGINNER', '무릎이 안쪽으로 모였어요. 발끝 방향으로 살짝 벌려보세요', 20),
(1, 'KNEE_IN',  'ADVANCED', '무릎 내전 발생. 둔근 외전 활성화 필요', 20),
(1, 'KNEE_IN',  'DIET',     '무릎 방향만 잡으면 하체 전체에 자극이 들어가요', 20),
(1, 'KNEE_IN',  'REHAB',    '무릎이 안쪽으로 들어가고 있습니다. 무리하지 말고 교정해주세요', 20),
-- HIP_HIGH (엉덩이 과도하게 들림)
(1, 'HIP_HIGH', 'BEGINNER', '엉덩이를 더 내려보세요. 깊게 앉을수록 효과가 커요', 30),
(1, 'HIP_HIGH', 'ADVANCED', 'ROM 부족. 골반 후방 경사와 햄스트링 가동성 확인 필요', 30),
(1, 'HIP_HIGH', 'DIET',     '더 깊게 앉으면 칼로리 소모가 늘어나요', 30),
(1, 'HIP_HIGH', 'REHAB',    '안전한 범위 내에서 가능한 만큼만 내려가주세요', 30),
-- BACK_BENT (등 굽음)
(1, 'BACK_BENT','BEGINNER', '허리를 곧게 펴주세요. 가슴을 살짝 들면 도움돼요', 5),
(1, 'BACK_BENT','ADVANCED', '흉추 굴곡 발생. 코어 활성화와 견갑골 안정화 필요', 5),
(1, 'BACK_BENT','DIET',     '허리 자세 유지하면 부상 없이 운동을 지속할 수 있어요', 5),
(1, 'BACK_BENT','REHAB',    '허리가 굽으면 부상 위험이 있습니다. 즉시 자세 교정해주세요', 5);

-- 런지 (id=2) — 페르소나 row 없음, NULL fallback 으로 호환 유지
INSERT INTO exercise_feedback_templates (exercise_id, feedback_type, persona, message, priority) VALUES
(2, 'KNEE_OUT',  NULL, '앞 무릎이 발끝을 넘지 않게 해주세요', 10),
(2, 'BACK_BENT', NULL, '상체를 곧게 세워주세요', 5),
(2, 'HIP_HIGH',  NULL, '뒷무릎을 더 굽혀주세요', 20);

-- 플랭크 (id=3) — 페르소나 row 없음, NULL fallback 으로 호환 유지
INSERT INTO exercise_feedback_templates (exercise_id, feedback_type, persona, message, priority) VALUES
(3, 'HIP_HIGH',  NULL, '엉덩이를 너무 들지 마세요', 10),
(3, 'HIP_LOW',   NULL, '엉덩이가 처지지 않게 들어주세요', 10),
(3, 'HEAD_DOWN', NULL, '고개를 너무 숙이지 마세요', 30),
(3, 'BACK_BENT', NULL, '몸을 일직선으로 유지해주세요', 5);

-- 4. 리포트 데이터
REPLACE INTO reports (id, session_id, member_id, report_type, summary, improvement_tips, created_at) VALUES
(701, 601, 1, 'SESSION', '601번 리포트', '안정적입니다.', NOW()),
(702, 602, 1, 'SESSION', '602번 리포트', '안정적입니다.', NOW()),
(703, 603, 1, 'SESSION', '603번 리포트', '안정적입니다.', NOW()),
(717, 617, 1, 'SESSION', '617번 리포트', '안정적입니다.', NOW()),
(718, 618, 1, 'SESSION', '618번 리포트', '안정적입니다.', NOW()),
(719, 619, 1, 'SESSION', '719번 리포트', '안정적입니다.', NOW());

SET FOREIGN_KEY_CHECKS = 1;