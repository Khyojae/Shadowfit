-- 인코딩 강제 (한글 깨짐 방지). 클라이언트 charset이 latin1 이어도 utf8mb4 로 협상.
SET NAMES utf8mb4;

-- 1. 데이터베이스 생성 및 선택
CREATE DATABASE IF NOT EXISTS shadowfit;
USE shadowfit;

-- 2. 사용자 테이블 (Member)
CREATE TABLE IF NOT EXISTS users (
                                     id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                     email VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    username VARCHAR(50) UNIQUE NOT NULL,
    sex ENUM('MALE', 'FEMALE', 'NONE') DEFAULT 'NONE',
    role VARCHAR(20) DEFAULT 'ROLE_USER',
    profile_image_url VARCHAR(500),
    height DECIMAL(5,1),
    weight DECIMAL(5,1),
    workout_level VARCHAR(20),
    selected_persona ENUM('BEGINNER', 'ADVANCED', 'DIET', 'REHAB') NOT NULL DEFAULT 'BEGINNER',
    preferred_url VARCHAR(500),
    onboarding_completed BOOLEAN DEFAULT FALSE,
    tts_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    tts_speed DECIMAL(3,1) NOT NULL DEFAULT 1.0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- DATETIME 대신 TIMESTAMP 권장 (타임존 대응)
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP -- 자동 갱신 설정
    );

-- 3. 운동 종목 마스터
CREATE TABLE IF NOT EXISTS exercises (
                                         id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                         name VARCHAR(100) NOT NULL,
    category ENUM('LOWER', 'BACK', 'UPPER', 'CORE', 'FULL') NOT NULL,
    description TEXT,
    preferred_url VARCHAR(500),
    target_joints JSON,
    sync_threshold_beginner DECIMAL(5,2) DEFAULT 60.00,
    sync_threshold_advanced DECIMAL(5,2) DEFAULT 85.00,
    expected_duration_minutes INT DEFAULT 15,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- DEFAULT 추가
    );

-- 4. 운동별 기준 자세 데이터
CREATE TABLE IF NOT EXISTS exercise_references (
                                                   id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                   exercise_id BIGINT NOT NULL,
                                                   timestamp_sec DECIMAL(10,3) NOT NULL,
    joint_coordinates JSON NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- DEFAULT 추가
    FOREIGN KEY (exercise_id) REFERENCES exercises(id) ON DELETE CASCADE,
    INDEX idx_exercise_ref_id (exercise_id)
    );

-- 5. 운동 세션
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
    status ENUM('IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'FAILED') DEFAULT 'IN_PROGRESS',
    version BIGINT NOT NULL DEFAULT 0, -- 낙관적 락 (JPA @Version)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- DEFAULT 추가
    FOREIGN KEY (member_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (exercise_id) REFERENCES exercises(id)
    );

-- 6. 자세 데이터
CREATE TABLE IF NOT EXISTS pose_data (
                                         id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                         session_id BIGINT NOT NULL,
                                         timestamp_sec DECIMAL(10,3) NOT NULL, -- [수정] 소수점 타임스탬프 대응을 위해 DECIMAL로 변경
    joint_coordinates JSON NOT NULL,
    sync_rate DECIMAL(5,2) NOT NULL,
    is_correct BOOLEAN DEFAULT TRUE,
    feedback_message VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES exercise_sessions(id) ON DELETE CASCADE,
    INDEX idx_session_timestamp (session_id, timestamp_sec)
    );

-- 7. 달력 일지
CREATE TABLE IF NOT EXISTS daily_logs (
                                          id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                          member_id BIGINT NOT NULL,
                                          log_date DATE NOT NULL,
                                          memo TEXT,
                                          total_exercise_time INT DEFAULT 0,
                                          total_calories DECIMAL(7,2) DEFAULT 0,
    mood ENUM('GREAT', 'GOOD', 'NORMAL', 'BAD', 'TERRIBLE'),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (member_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uk_member_date (member_id, log_date)
    );

-- 8. 운동 보고서
CREATE TABLE IF NOT EXISTS reports (
                                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                       member_id BIGINT NOT NULL,
                                       session_id BIGINT NOT NULL,
                                       report_type ENUM('SESSION', 'WEEKLY', 'MONTHLY') DEFAULT 'SESSION',
    summary TEXT,
    detailed_analysis JSON,
    improvement_tips TEXT,
    comparison_with_previous JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- DEFAULT 추가
    FOREIGN KEY (member_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (session_id) REFERENCES exercise_sessions(id) ON DELETE CASCADE
    );

-- 9-A. 운동별 피드백 메시지 템플릿 (TTS 멘트, 페르소나별 분기)
-- persona NULL row 는 페르소나 row 없을 때 fallback 으로 사용 (분기 4-A + BE-13)
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

-- 9-B. 세션 피드백 판정 이벤트 로그 (AI 가 BT-SET 으로 batch 송신, 멱등성 보장)
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