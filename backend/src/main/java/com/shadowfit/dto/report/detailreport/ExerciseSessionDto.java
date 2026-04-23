package com.shadowfit.dto.report.detailreport;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExerciseSessionDto {
    private Long sessionId;              // 클릭 시 이동을 위한 ID
    private String exerciseName;         // "숄더프레스"
    private String setSummary;           // "4세트 x 10회"
    private double syncRate;             // 93
}
