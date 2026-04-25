package com.shadowfit.dto.onboarding;

import com.shadowfit.model.member.SelectedPersona;
import com.shadowfit.model.member.WorkoutLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "온보딩 멤버 정보 수정 req dto")
public class OnboardingRequestDto {

    @Schema(description = "목표 페르소나 설정", example = "ADVANCED", requiredMode = Schema.RequiredMode.REQUIRED)
    private SelectedPersona selectedPersona;

    @Schema(description = "나의 운동 레벨", example = "STARTER", requiredMode = Schema.RequiredMode.REQUIRED)
    private WorkoutLevel workoutLevel;

    @Schema(description = "키 (cm 단위)", example = "180.0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double height;

    @Schema(description = "몸무게 (kg 단위)", example = "75.5", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double weight;
}
