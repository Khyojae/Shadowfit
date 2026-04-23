package com.shadowfit.dto.report.detailreport;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SessionReportResponseDto {
    private Long sessionId;
    private int avgSyncRate;
    private int totalReps;
    private int workoutMinutes;
    private int caloriesBurned;
    private String aiSafetyReport;

    private WorstSectionDto worstSection;
    private List<ExerciseSyncRateDto> syncRateDetails;

    private ComparisonWithPreviousDto comparisonWithPrevious;
}
