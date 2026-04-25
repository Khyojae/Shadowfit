package com.shadowfit.dto.exercises.session;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "운동 세션 종료 및 결과 업데이트 요청 DTO")
public class SessionUpdateRequestDto {
    @Schema(description = "총 반복 횟수", example = "15", requiredMode = Schema.RequiredMode.REQUIRED)
    private int totalReps;

    @Schema(description = "평균 싱크로율 (0.0~100.0)", example = "82.5", requiredMode = Schema.RequiredMode.REQUIRED)
    private double avgSyncRate;

    @Schema(description = "최고 싱크로율", example = "95.0")
    private double maxSyncRate;

    @Schema(description = "최저 싱크로율", example = "40.0")
    private double minSyncRate;

    @Schema(description = "소모 칼로리", example = "120.5")
    private double caloriesBurned;

    @Schema(description = "난이도 레벨 (1~5)", example = "3")
    private int difficultyLevel;
}
