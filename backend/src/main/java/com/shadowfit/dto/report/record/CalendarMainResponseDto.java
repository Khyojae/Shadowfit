package com.shadowfit.dto.report.record;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CalendarMainResponseDto {
    // 1. 상단 요약 카드 데이터 (빨간색 동그라미 부분)
    private int monthlyExerciseDays;     // 이번 달 운동 일수 (예: 11일)
    private int totalAvgSyncRate;        // 이번 달 평균 싱크로율 (예: 84%)
    private int consecutiveDays;         // 연속 운동 기록 (예: 5일)

    // 2. 달력 표시용 데이터
    private int year;
    private int month;
    private List<CalendarDayDto> records; // 날짜별 운동 여부 및 대표 기록
}
