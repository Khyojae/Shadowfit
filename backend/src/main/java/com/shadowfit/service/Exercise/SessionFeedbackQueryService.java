package com.shadowfit.service.Exercise;

import com.shadowfit.dto.exercises.feedback.SessionFeedbackEventDto;
import com.shadowfit.dto.exercises.feedback.SessionFeedbackSummaryDto;
import com.shadowfit.global.error.BusinessException;
import com.shadowfit.global.error.ErrorCode;
import com.shadowfit.model.exercise.Session;
import com.shadowfit.repository.exercise.SessionFeedbackLogRepository;
import com.shadowfit.repository.exercise.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionFeedbackQueryService {
    private final SessionFeedbackLogRepository feedbackLogRepository;
    private final SessionRepository sessionRepository;

    public List<SessionFeedbackEventDto> getEvents(Long sessionId, Long currentMemberId) {
        ensureOwnership(sessionId, currentMemberId);
        return feedbackLogRepository.findBySessionIdOrderByOccurredAtAsc(sessionId).stream()
                .map(SessionFeedbackEventDto::fromEntity)
                .toList();
    }

    public SessionFeedbackSummaryDto getSummary(Long sessionId, Long currentMemberId) {
        ensureOwnership(sessionId, currentMemberId);

        var stats = feedbackLogRepository.aggregateBySession(sessionId);
        long total = stats.stream().mapToLong(s -> s.getCount() != null ? s.getCount() : 0L).sum();

        List<SessionFeedbackSummaryDto.TypeBucket> buckets = stats.stream()
                .map(s -> new SessionFeedbackSummaryDto.TypeBucket(
                        s.getFeedbackType(),
                        s.getCount(),
                        scale(s.getAvgSyncRate()),
                        s.getMinSyncRate(),
                        s.getMaxSyncRate()))
                .toList();

        return new SessionFeedbackSummaryDto(sessionId, total, buckets);
    }

    private void ensureOwnership(Long sessionId, Long currentMemberId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        if (!session.getMember().getId().equals(currentMemberId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    private static BigDecimal scale(BigDecimal v) {
        return v == null ? null : v.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
