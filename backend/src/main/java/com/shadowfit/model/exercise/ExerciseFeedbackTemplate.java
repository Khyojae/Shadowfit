package com.shadowfit.model.exercise;

import com.shadowfit.model.member.SelectedPersona;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 운동별 자세 문제에 대해 사용자에게 안내할 피드백 메시지 템플릿.
 * persona NULL row 는 페르소나 row 없을 때의 fallback (분기 4-A + BE-13).
 */
@Entity
@Table(name = "exercise_feedback_templates",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_exercise_feedback_persona",
               columnNames = {"exercise_id", "feedback_type", "persona"}))
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExerciseFeedbackTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_id", nullable = false)
    private Exercise exercise;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_type", nullable = false, length = 30)
    private FeedbackType feedbackType;

    /** null 이면 모든 페르소나 공통 fallback. */
    @Enumerated(EnumType.STRING)
    @Column(name = "persona", length = 10)
    private SelectedPersona persona;

    @Column(nullable = false, length = 200)
    private String message;

    /** 동시에 여러 문제가 감지될 때 우선순위 (낮을수록 우선). */
    @Builder.Default
    @Column(nullable = false)
    private Integer priority = 100;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}