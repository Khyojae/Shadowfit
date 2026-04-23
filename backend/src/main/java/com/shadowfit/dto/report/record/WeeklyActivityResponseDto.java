package com.shadowfit.dto.report.record;

import com.shadowfit.dto.report.detailreport.ExerciseSessionDto;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeeklyActivityResponseDto {
    private String dateRange;            // "3월 23일 - 29일"
    private int totalWorkouts;           // 4 Workouts
    private int totalMinutes;            // 35 Min
    private int totalCalories;           // 250 Kcal

    private List<DailyLogSummaryDto> dailyLogs; // 요일별 막대 그래프용 데이터
    private List<ExerciseSessionDto> todayDetails; // "3월 23일 운동 상세" 리스트
}
