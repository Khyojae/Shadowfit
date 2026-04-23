package com.shadowfit.dto.exercises.session;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "운동세션 시작 req dto")
public class ExercisesRequestDto {
    @Schema(description = "운동 ID",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer exerciseId;

    @Schema(description = "유튜브 링크",
            example = "https://youtu.be/xxx",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String referenceSource;
}
