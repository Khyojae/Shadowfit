package com.shadowfit.dto.report.record;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DailyLogRequestDto {

    // 날짜 정보를 "2026-03-31" 형식의 문자열로 안전하게 받기 위해 사용
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "Asia/Seoul")
    private LocalDate logDate;

    // 사용자가 입력한 짧은 운동 소감 또는 메모
    private String memo;

    // 그날의 기분 상태 (GREAT, GOOD, NORMAL, BAD, TERRIBLE)
    private Mood mood;
}