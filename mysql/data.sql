-- 1. 사용자 데이터 (Member)
-- 수정: user_id 삭제 -> username 사용 / height, weight (Double) 추가
INSERT INTO users (email, password, username, nickname, sex, selected_persona, workout_level, onboarding_completed, role, height, weight)
VALUES
    ('beginner@test.com', '{noop}1234', 'user_01', '헬린이1', 'MALE', 'BEGINNER', 'STARTER', TRUE, 'USER', 175.5, 70.2),
    ('pro@test.com', '{noop}1234', 'user_02', '득근득근', 'FEMALE', 'ADVANCED', 'PRO', TRUE, 'USER', 162.0, 52.5),
    ('diet@test.com', '{noop}1234', 'user_03', '다이어터', 'NONE', 'DIET', 'BEGINNER', TRUE, 'USER', 180.0, 85.3);

-- 2. 운동 종목 마스터 데이터 (동일)
INSERT INTO exercises (name, category, description, target_joints, sync_threshold_beginner, sync_threshold_advanced)
VALUES
    ('스쿼트', 'LOWER', '하체 운동의 꽃입니다. 무릎이 발끝을 나가지 않도록 주의하세요.',
     '["left_hip", "right_hip", "left_knee", "right_knee", "left_ankle", "right_ankle"]', 60.0, 85.0),
    ('푸쉬업', 'UPPER', '가슴과 삼두근을 발달시키는 운동입니다.',
     '["left_shoulder", "right_shoulder", "left_elbow", "right_elbow", "left_wrist", "right_wrist"]', 55.0, 80.0),
    ('데드리프트', 'BACK', '전신 후면 근육을 강화합니다.',
     '["left_hip", "right_hip", "left_knee", "right_knee", "left_shoulder", "right_shoulder"]', 65.0, 90.0);

-- 3. 운동 세션
-- 수정: user_id -> member_id 명칭 변경 반영
INSERT INTO exercise_sessions (member_id, exercise_id, start_time, end_time, total_reps, avg_sync_rate, max_sync_rate, status)
VALUES
    (1, 1, '2026-04-01 09:00:00', '2026-04-01 09:15:00', 30, 78.5, 92.0, 'COMPLETED'),
    (1, 2, '2026-04-02 10:00:00', '2026-04-02 10:10:00', 20, 65.2, 80.0, 'COMPLETED');

-- 4. 자세 데이터 (동일)
INSERT INTO pose_data (session_id, timestamp_sec, joint_coordinates, sync_rate, is_correct, feedback_message)
VALUES
    (1, 1, '{"point_0": [0.5, 0.5, 0.1], "point_1": [0.52, 0.48, 0.12]}', 85.5, TRUE, '좋은 자세입니다!'),
    (1, 2, '{"point_0": [0.49, 0.51, 0.1], "point_1": [0.53, 0.47, 0.11]}', 45.0, FALSE, '무릎이 너무 안쪽으로 모이고 있어요.'),
    (1, 3, '{"point_0": [0.5, 0.5, 0.1], "point_1": [0.52, 0.48, 0.12]}', 90.2, TRUE, '완벽하게 교정되었습니다.');

-- 5. 달력 일지
-- 수정: user_id -> member_id 명칭 변경 반영
INSERT INTO daily_logs (member_id, log_date, memo, total_exercise_time, total_calories, mood)
VALUES
    (1, '2026-04-01', '첫 스쿼트 완료! 땀나고 좋다.', 15, 120.5, 'GREAT'),
    (1, '2026-04-02', '푸쉬업은 역시 힘들다.', 10, 85.0, 'GOOD');

-- 6. 신체 변화 기록
-- 수정: user_id -> member_id 명칭 변경 반영
INSERT INTO body_records (member_id, record_date, weight, body_fat_percentage, muscle_mass)
VALUES
    (1, '2026-03-01', 75.5, 22.1, 32.5),
    (1, '2026-04-01', 74.8, 21.5, 33.1);

-- 7. 운동 보고서
-- 수정: user_id -> member_id 명칭 변경 반영
INSERT INTO reports (member_id, session_id, report_type, summary, detailed_analysis, improvement_tips)
VALUES
    (1, 1, 'SESSION', '전체적으로 안정적인 스쿼트였습니다.',
     '{"stuck_points": [12, 25], "stability_score": 8.5}', '하강 시 속도를 조금 더 늦추면 자극이 더 잘 올 것 같습니다.');