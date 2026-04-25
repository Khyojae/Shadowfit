package com.shadowfit.dto.onboarding;

import com.shadowfit.model.member.Member;
import com.shadowfit.model.member.SelectedPersona;
import com.shadowfit.model.member.WorkoutLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "온보딩 멤버 정보 조회 res dto")
public class OnboardingDto {
    @Schema(description = "시스템 고유 번호 (PK)", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(description = "사용자 계정 아이디 (표시용)", example = "shadow_runner", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @Schema(description = "선택한 운동 페르소나", example = "DIET", requiredMode = Schema.RequiredMode.REQUIRED)
    private SelectedPersona selectedPersona;

    @Schema(description = "현재 운동 숙련도", example = "BEGINNER", requiredMode = Schema.RequiredMode.REQUIRED)
    private WorkoutLevel workoutLevel;

    @Schema(description = "신장 (cm)", example = "178.5", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double height;

    @Schema(description = "체중 (kg)", example = "72.3", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double weight;

    public static OnboardingDto fromEntity(Member member){
        return OnboardingDto.builder()
                .id(member.getId())
                .username(member.getUsername())
                .selectedPersona(member.getSelectedPersona())
                .workoutLevel(member.getWorkoutLevel())
                .height(member.getHeight())
                .weight(member.getWeight())
                .build();
    }
}