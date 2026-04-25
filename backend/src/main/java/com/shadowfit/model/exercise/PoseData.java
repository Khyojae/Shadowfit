package com.shadowfit.model.exercise;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "pose_data")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@ToString(exclude = "session") // 무한 참조 방지
public class PoseData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @Column(name = "timestamp_sec", nullable = false)
    private Double timestampSec; // 영상 내 시간대 (초)

    @Lob // 데이터가 길 수 있으므로 대용량 데이터 타입 지정
    @Column(columnDefinition = "TEXT", nullable = false)
    private String jointCoordinates; // 관절 좌표 (JSON 문자열)

    private Double syncRate; // 정답 영상과의 일치율

    private Boolean isCorrect; // 자세 정답 여부

    @Column(length = 500)
    private String feedbackMessage; // AI가 주는 실시간 피드백
}