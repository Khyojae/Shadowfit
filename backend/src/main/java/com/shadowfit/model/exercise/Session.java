package com.shadowfit.model.exercise;

import com.shadowfit.model.member.Member;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "exercise_sessions")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"member", "exercise"}) // 무한 참조 방지
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 연관관계 설정 (N:1)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_id", nullable = false)
    private Exercise exercise;

    @Column(length = 500)
    private String referenceSource;

    @Column(nullable = false)
    private LocalDateTime startTime;

    private LocalDateTime endTime;

    @Builder.Default
    private Integer totalReps = 0;

    @Column(precision = 5, scale = 2)
    private BigDecimal avgSyncRate;

    @Column(precision = 5, scale = 2)
    private BigDecimal maxSyncRate;

    @Column(precision = 5, scale = 2)
    private BigDecimal minSyncRate;

    @Column(precision = 7, scale = 2)
    private BigDecimal caloriesBurned;

    @Builder.Default
    private Integer difficultyLevel = 1;

    @Enumerated(EnumType.STRING) // 숫자가 아닌 문자열 이름으로 저장
    @Builder.Default
    private Status status = Status.IN_PROGRESS;

    @CreationTimestamp // INSERT 시 현재 시간 자동 입력
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
