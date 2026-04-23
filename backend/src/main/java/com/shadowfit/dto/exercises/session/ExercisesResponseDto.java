package com.shadowfit.dto.exercises.session;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shadowfit.model.exercise.Status;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "운동세션 시작 res dto")
public class ExercisesResponseDto {
    @Schema(description = "세션 id",
            requiredMode = Schema.RequiredMode.REQUIRED)
    public Integer sessionId;

    @Schema(description = "운동 id",
            requiredMode = Schema.RequiredMode.REQUIRED)
    public Integer exerciseId;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "시작 시간",
            requiredMode = Schema.RequiredMode.REQUIRED)
    public LocalDateTime startTime;

    @Schema(description = "상태",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @Builder.Default
    public Status status = Status.IN_PROGRESS;
}
