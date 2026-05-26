package com.shadowfit.model.exercise;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 세션 진행 중 AI 가 판정한 피드백 이벤트 로그 (분기 2-A 의미 재정의).
 * AI 가 BT-SET 으로 세트 경계마다 batch 송신 (분기 2.A.BT). 휴식 시간 retry 가능.
 *
 * 멱등성 (BE-13-G):
 *   uniqueKey (session_id, occurred_at, feedback_type) 로 중복 row 방지.
 *   FeedbackLogService 가 per-row try/catch 로 DataIntegrityViolationException 흡수.
 */
@Entity
@Table(name = "session_feedback_logs",
       indexes = @Index(name = "idx_session_feedback", columnList = "session_id, occurred_at"),
       uniqueConstraints = @UniqueConstraint(
               name = "uk_session_event",
               columnNames = {"session_id", "occurred_at", "feedback_type"}))
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionFeedbackLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_type", nullable = false, length = 30)
    private FeedbackType feedbackType;

    /** 트리거 순간의 싱크로율 (0.0 ~ 100.0). FastAPI가 측정한 값. */
    @Column(name = "sync_rate_at_trigger", precision = 5, scale = 2)
    private BigDecimal syncRateAtTrigger;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}