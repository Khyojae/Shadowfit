package com.shadowfit.dto.exercises.feedback;

import com.shadowfit.model.exercise.FeedbackType;

import java.math.BigDecimal;
import java.util.List;

/** GET /sessions/{id}/feedback-summary — 결함 카운트 + sync_rate 통계 (협의 #17). */
public record SessionFeedbackSummaryDto(
        Long sessionId,
        Long totalCount,
        List<TypeBucket> byType
) {
    public record TypeBucket(
            FeedbackType feedbackType,
            Long count,
            BigDecimal avgSyncRate,
            BigDecimal minSyncRate,
            BigDecimal maxSyncRate
    ) {}
}
