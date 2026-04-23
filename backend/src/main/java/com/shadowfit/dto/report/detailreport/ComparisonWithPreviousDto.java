package com.shadowfit.dto.report.detailreport;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ComparisonWithPreviousDto {
    private int syncRateDiff;       // 예: +5
    private int workoutMinutesDiff;  // 예: -2
    private int caloriesDiff;        // 예: +30
}