package com.shadowfit.dto.report.record;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shadowfit.model.report.DailyLog;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DailyLogResponseDto {
    private Long id;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate logDate;
    private String memo;
    private Integer totalExerciseTime; // 분 단위
    private BigDecimal totalCalories;
    private Mood mood;

    /**
     * Entity -> DTO 변환 생성자
     * 서비스 레이어에서 .map(DailyLogResponseDto::new) 처럼 쓰기 편하게 추가합니다.
     */
    public DailyLogResponseDto(DailyLog entity) {
        this.id = entity.getId();
        this.logDate = entity.getLogDate();
        this.memo = entity.getMemo();
        this.totalExerciseTime = entity.getTotalExerciseTime();
        this.totalCalories = entity.getTotalCalories();
    }
}
