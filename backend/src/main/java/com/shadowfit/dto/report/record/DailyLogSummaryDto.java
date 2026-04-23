package com.shadowfit.dto.report.record;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DailyLogSummaryDto {
    private String dayOfWeek;    // "월", "화", "수" ... (또는 "MON", "TUE")
    private int workoutMinutes;  // 해당 요일의 운동 시간 (그래프 높이 결정)
    private boolean isToday;     // 오늘 날짜인지 여부 (디자인의 노란색 강조용)
}
