package com.shadowfit.model.exercise;

import jakarta.persistence.*;
import lombok.*;
import jakarta.persistence.Id;

@Entity
@Table(name = "exercise_references")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@ToString(exclude = "exercise") // 연관관계 제외 추가
public class ExerciseReference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_id", nullable = false) // 필수값 설정
    private Exercise exercise;

    @Column(name = "timestamp_sec", nullable = false)
    private Double timestampSec;

    @Column(name = "joint_coordinates", columnDefinition = "json", nullable = false)
    private String jointCoordinates;
}