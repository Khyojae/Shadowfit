package com.shadowfit.repository.exercise;

import com.shadowfit.model.exercise.FeedbackType;
import com.shadowfit.model.exercise.SessionFeedbackLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface SessionFeedbackLogRepository extends JpaRepository<SessionFeedbackLog, Long> {

    List<SessionFeedbackLog> findBySessionIdOrderByOccurredAtAsc(Long sessionId);

    @Query("SELECT l.feedbackType AS type, COUNT(l) AS count " +
           "FROM SessionFeedbackLog l WHERE l.session.id = :sessionId GROUP BY l.feedbackType")
    List<FeedbackTypeCount> countByTypeForSession(@Param("sessionId") Long sessionId);

    interface FeedbackTypeCount {
        FeedbackType getType();
        Long getCount();
    }

    /** BE-15 의 feedback-summary 용 — type 별 카운트 + sync_rate 통계 (협의 #17). */
    @Query("""
            SELECT l.feedbackType AS feedbackType,
                   COUNT(l)        AS count,
                   AVG(l.syncRateAtTrigger) AS avgSyncRate,
                   MIN(l.syncRateAtTrigger) AS minSyncRate,
                   MAX(l.syncRateAtTrigger) AS maxSyncRate
            FROM SessionFeedbackLog l
            WHERE l.session.id = :sessionId
            GROUP BY l.feedbackType
            ORDER BY count DESC
            """)
    List<TypeStats> aggregateBySession(@Param("sessionId") Long sessionId);

    interface TypeStats {
        FeedbackType getFeedbackType();
        Long getCount();
        BigDecimal getAvgSyncRate();
        BigDecimal getMinSyncRate();
        BigDecimal getMaxSyncRate();
    }
}