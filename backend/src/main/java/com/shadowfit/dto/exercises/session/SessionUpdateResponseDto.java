package com.shadowfit.dto.exercises.session;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shadowfit.model.exercise.Status;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "운동 세션 종료 응답 DTO")
public class SessionUpdateResponseDto {
    @Schema(description = "완료된 세션 ID", example = "101")
    private Integer sessionId;

    @Schema(description = "최종 상태", example = "COMPLETED")
    private Status status; // COMPLETED

    @Schema(description = "종료 시간", example = "2026-04-25T16:40:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime endTime;
}
