package com.shadowfit.dto.exercises;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "운동세션 시작 req dto(스프링->fastapi)")
public class FastApiRequestDto {

    @Schema(description = "운동 종목 ID", example = "1")
    private Long exerciseId;

    @Schema(description = "유튜브 원본 URL", example = "https://www.youtube.com/watch?v=xxx")
    private String youtubeUrl;

    @Schema(description = "생성된 세션 ID", example = "101")
    private Long sessionId;
}
