package com.shadowfit.dto.exercises.feedback;

import com.shadowfit.model.exercise.FeedbackType;
import com.shadowfit.model.exercise.SessionFeedbackLog;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** GET /sessions/{id}/feedbacks 의 단일 이벤트 표현. */
public record SessionFeedbackEventDto(
        Long id,
        FeedbackType feedbackType,
        BigDecimal syncRateAtTrigger,
        LocalDateTime occurredAt
) {
    public static SessionFeedbackEventDto fromEntity(SessionFeedbackLog log) {
        return new SessionFeedbackEventDto(
                log.getId(),
                log.getFeedbackType(),
                log.getSyncRateAtTrigger(),
                log.getOccurredAt()
        );
    }
}
