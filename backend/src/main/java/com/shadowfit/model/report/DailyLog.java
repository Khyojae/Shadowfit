package com.shadowfit.model.report;

import com.shadowfit.dto.report.record.Mood;
import com.shadowfit.model.member.Member;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "daily_logs", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "logDate"}) // 한 사용자가 같은 날짜에 두 개 못 쓰게 방지
})
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class DailyLog extends BaseTimeEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Member user;

    @Column(nullable = false)
    private LocalDate logDate; // 운동 날짜 (2026-03-31)

    @Column(columnDefinition = "TEXT")
    private String memo; // 사용자가 직접 쓴 일기/메모

    private Integer totalExerciseTime; // 당일 총 운동 시간 (분 단위)

    @Column(precision = 7, scale = 2)
    private BigDecimal totalCalories; // 당일 소모한 총 칼로리

    @Enumerated(EnumType.STRING)
    private Mood mood; // GREAT, GOOD, NORMAL, BAD, TERRIBLE

    // 비즈니스 로직: 운동 시간이나 칼로리를 누적 업데이트할 때 사용
    public void updateStats(int addTime, BigDecimal addCalories) {
        this.totalExerciseTime = (this.totalExerciseTime == null ? 0 : this.totalExerciseTime) + addTime;
        this.totalCalories = (this.totalCalories == null ? BigDecimal.ZERO : this.totalCalories).add(addCalories);
    }
}