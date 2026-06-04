package com.shadowfit.dto.report.record;

import com.shadowfit.dto.report.detailreport.ExerciseSessionDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 달력에서 특정 날짜를 눌렀을 때 그 날의 운동 목록을 돌려주는 응답.
 * 주간 요약의 todayDetails 와 동일한 {@link ExerciseSessionDto} 를 재사용한다.
 * (project-squat-first: 보통 하루 단건 → 프론트는 sessions[0].sessionId 로 상세 직행 가능)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyActivityResponseDto {
    private String date;                       // "2026-06-04"
    private int totalWorkouts;                 // 그 날 세션 수
    private List<ExerciseSessionDto> sessions; // 그 날 운동 리스트 (없으면 빈 배열)
}
