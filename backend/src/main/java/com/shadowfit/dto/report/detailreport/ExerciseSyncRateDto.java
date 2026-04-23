package com.shadowfit.dto.report.detailreport;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseSyncRateDto {
    private Long exerciseId;
    private String name;                 // "숄더프레스"
    private String setInfo;              // "4세트 x 10회"
    private double syncRate;             // 93.0
}
