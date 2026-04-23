package com.shadowfit.dto.report.record;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CalendarDayDto {
    private String date;                 // "2026-03-31"
    private boolean hasRecord;           // 운동 기록 존재 여부 (달력 점 표시용)
    private Double dailyAvgSyncRate;     // 해당 날짜의 평균 싱크로율
}
