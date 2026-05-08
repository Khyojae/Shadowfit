-- 1. 기존 데이터 및 테이블 정리
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS body_records, reports, daily_logs, pose_data, exercise_sessions, exercise_references, exercises, users;
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

-- 4. 리포트 데이터
REPLACE INTO reports (id, session_id, member_id, report_type, summary, improvement_tips, created_at) VALUES
(701, 601, 1, 'SESSION', '601번 리포트', '안정적입니다.', NOW()),
(702, 602, 1, 'SESSION', '602번 리포트', '안정적입니다.', NOW()),
(703, 603, 1, 'SESSION', '603번 리포트', '안정적입니다.', NOW()),
(717, 617, 1, 'SESSION', '617번 리포트', '안정적입니다.', NOW()),
(718, 618, 1, 'SESSION', '618번 리포트', '안정적입니다.', NOW()),
(719, 619, 1, 'SESSION', '719번 리포트', '안정적입니다.', NOW());

SET FOREIGN_KEY_CHECKS = 1;