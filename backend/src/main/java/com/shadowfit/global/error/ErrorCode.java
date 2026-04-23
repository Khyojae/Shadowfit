package com.shadowfit.global.error;


import lombok.Getter;

@Getter
public enum ErrorCode {


    // --- Common ---
    INVALID_INPUT_VALUE(400, "C001", "올바르지 않은 입력값입니다."),
    METHOD_NOT_ALLOWED(405, "C002", "허용되지 않은 HTTP 메서드입니다."),
    INTERNAL_SERVER_ERROR(500, "C003", "서버 내부 오류가 발생했습니다."),
    INVALID_TYPE_VALUE(400, "C004", "입력값의 타입이 적절하지 않습니다."),
    HANDLE_ACCESS_DENIED(403, "C005", "접근이 거부되었습니다."),

    // --- Auth ---
    UNAUTHORIZED(401, "A001", "로그인이 필요한 서비스입니다."),
    ACCESS_DENIED(403, "A002", "해당 리소스에 대한 접근 권한이 없습니다."),
    TOKEN_EXPIRED(401, "A003", "인증 토큰이 만료되었습니다."),
    INVALID_TOKEN(401, "A004", "잘못된 인증 토큰입니다."),
    LOGIN_INPUT_INVALID(401, "A005", "비밀번호가 틀렸습니다."),

    // --- User & Persona ---
    USER_NOT_FOUND(404, "U001", "존재하지 않는 사용자입니다."),
    INVALID_PERSONA_TYPE(400, "U002", "유효하지 않은 페르소나 설정입니다."),
    USERID_DUPLICATION(400, "U003", "이미 가입된 사용자입니다."),

    // --- Workout Session  ---
    EXERCISE_NOT_FOUND(404, "W001", "존재하지 않는 운동 종목입니다."),
    METADATA_NOT_FOUND(404, "W002", "운동 메타데이터(JSON/Video)를 찾을 수 없습니다."),
    SESSION_NOT_FOUND(404, "W003", "진행 중인 운동 세션을 찾을 수 없습니다."),
    S3_UPLOAD_ERROR(500, "W004", "파일 저장소(S3) 연결에 실패했습니다."),

    // --- F10-1 Filtering Engine ---
    LOW_SYNC_RATE(400, "V001", "운동 싱크로율이 너무 낮아 기록되지 않았습니다."),
    INVALID_WORKOUT_DATA(400, "V002", "부정행위 또는 유효하지 않은 움직임이 감지되었습니다."),
    INSUFFICIENT_COUNT(400, "V003", "최소 운동 횟수를 채우지 못했습니다."),
    DATA_INTEGRITY_VIOLATION(422, "V004", "전달된 좌표 데이터가 손상되었거나 형식이 맞지 않습니다."),

    // --- AI & GPT Factory ---
    AI_FEEDBACK_FAILED(503, "AI001", "AI 피드백 생성 중 오류가 발생했습니다."),
    PROMPT_TEMPLATE_ERROR(500, "AI002", "GPT 프롬프트 생성 로직에 오류가 발생했습니다."),
    AI_QUOTA_EXCEEDED(429, "AI003", "AI 서비스 호출 할당량을 초과했습니다."),

    // --- Infrastructure & Cache  ---
    REDIS_CONNECTION_FAILURE(500, "I001", "캐시 서버 연결에 실패했습니다."),
    API_RESPONSE_TIMEOUT(504, "I002", "API 응답 시간이 초과되었습니다. (Threshold: 500ms)"),
    DATABASE_LOCK_FAILURE(500, "I003", "데이터베이스 트랜잭션 처리 중 오류가 발생했습니다."),

    //Report
    REPORT_NOT_FOUND(404,"R001","리포트를 찾을 수 없습니다");

    private final int status;
    private final String code;
    private final String message;

    ErrorCode(final int status, final String code, final String message) {
        this.status = status;
        this.message = message;
        this.code = code;
    }
}
