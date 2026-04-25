-- 1. 데이터베이스 생성 및 선택
CREATE DATABASE IF NOT EXISTS shadowfit;
USE shadowfit;

-- 2. 사용자 테이블 (Member)
CREATE TABLE IF NOT EXISTS users (
                                     id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                     email VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    username VARCHAR(50) UNIQUE NOT NULL, -- 일반 아이디
    sex ENUM('MALE', 'FEMALE', 'NONE') DEFAULT 'NONE',
    role VARCHAR(20) DEFAULT 'ROLE_USER',
    profile_image_url VARCHAR(500),
    height DECIMAL(5,1),
    weight DECIMAL(5,1),
    workout_level VARCHAR(20),
    selected_persona ENUM('BEGINNER', 'ADVANCED', 'DIET', 'REHAB') NOT NULL DEFAULT 'BEGINNER',
    onboarding_completed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    );

-- 3. 운동 종목 마스터
CREATE TABLE exercises (
                           id BIGINT AUTO_INCREMENT PRIMARY KEY,
                           name VARCHAR(100) NOT NULL,
                           category ENUM('LOWER', 'BACK', 'UPPER', 'CORE', 'FULL') NOT NULL,
                           description TEXT,
                           reference_video_url VARCHAR(500),
                           target_joints JSON,
                           sync_threshold_beginner DECIMAL(5,2) DEFAULT 60.00,
                           sync_threshold_advanced DECIMAL(5,2) DEFAULT 85.00,
                           created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 4. 운동별 기준 자세 데이터
CREATE TABLE IF NOT EXISTS exercise_references (
                                                   id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                   exercise_id BIGINT NOT NULL,
                                                   timestamp_sec DECIMAL(10,3) NOT NULL,
    joint_coordinates JSON NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (exercise_id) REFERENCES exercises(id),
    INDEX idx_exercise_ref_id (exercise_id)
    );

-- 5. 운동 세션 (user_id -> member_id 변경)
CREATE TABLE exercise_sessions (
                                   id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                   member_id BIGINT NOT NULL,           -- 수정 완료
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
                                   status ENUM('IN_PROGRESS', 'COMPLETED', 'CANCELLED') DEFAULT 'IN_PROGRESS',
                                   created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   FOREIGN KEY (member_id) REFERENCES users(id),
                                   FOREIGN KEY (exercise_id) REFERENCES exercises(id)
);

-- 6. 자세 데이터
CREATE TABLE pose_data (
                           id BIGINT AUTO_INCREMENT PRIMARY KEY,
                           session_id BIGINT NOT NULL,
                           timestamp_sec INT NOT NULL,
                           joint_coordinates JSON NOT NULL,
                           sync_rate DECIMAL(5,2) NOT NULL,
                           is_correct BOOLEAN DEFAULT TRUE,
                           feedback_message VARCHAR(500),
                           FOREIGN KEY (session_id) REFERENCES exercise_sessions(id),
                           INDEX idx_session_timestamp (session_id, timestamp_sec)
);

-- 7. 달력 일지 (user_id -> member_id 변경)
CREATE TABLE daily_logs (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            member_id BIGINT NOT NULL,          -- 수정 완료
                            log_date DATE NOT NULL,
                            memo TEXT,
                            total_exercise_time INT DEFAULT 0,
                            total_calories DECIMAL(7,2) DEFAULT 0,
                            mood ENUM('GREAT', 'GOOD', 'NORMAL', 'BAD', 'TERRIBLE'),
                            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                            FOREIGN KEY (member_id) REFERENCES users(id),
                            UNIQUE KEY uk_member_date (member_id, log_date) -- 제약 조건 이름 수정
);

-- 8. 운동 보고서 (user_id -> member_id 변경)
CREATE TABLE reports (
                         id BIGINT AUTO_INCREMENT PRIMARY KEY,
                         member_id BIGINT NOT NULL,          -- 수정 완료
                         session_id BIGINT NOT NULL,
                         report_type ENUM('SESSION', 'WEEKLY', 'MONTHLY') DEFAULT 'SESSION',
                         summary TEXT,
                         detailed_analysis JSON,
                         improvement_tips TEXT,
                         comparison_with_previous JSON,
                         created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         FOREIGN KEY (member_id) REFERENCES users(id),
                         FOREIGN KEY (session_id) REFERENCES exercise_sessions(id)
);

-- 9. 신체 변화 기록 (user_id -> member_id 변경)
CREATE TABLE body_records (
                              id BIGINT AUTO_INCREMENT PRIMARY KEY,
                              member_id BIGINT NOT NULL,          -- 수정 완료
                              record_date DATE NOT NULL,
                              weight DECIMAL(5,1),
                              body_fat_percentage DECIMAL(4,1),
                              muscle_mass DECIMAL(5,1),
                              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              FOREIGN KEY (member_id) REFERENCES users(id),
                              INDEX idx_member_date (member_id, record_date)
);