package com.shadowfit.dto.exercises;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "AI 분석 좌표 데이터 수신 DTO")
public class PoseDataRequestDto {

    @Schema(description = "운동 세션 ID", example = "1")
    private Long sessionId;

    @Schema(description = "영상 내 시간(초)", example = "1.25")
    private Double timestampSec;

    @Schema(description = "관절 좌표 (JSON 문자열)", example = "{\"nose\": [0.5, 0.5] ...}")
    private String jointCoordinates;


}