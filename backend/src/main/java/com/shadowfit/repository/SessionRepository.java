package com.shadowfit.repository;

import com.shadowfit.model.exercise.Session;
import com.shadowfit.model.exercise.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SessionRepository extends JpaRepository<Session,Long> {
    @Query("SELECT s FROM Session s JOIN FETCH s.exercise WHERE s.id = :sessionId")
    Optional<Session> findSessionWithExerciseById(@Param("sessionId") Long sessionId);

    // [중요] 이전 기록과 비교하기 위해 바로 직전 세션 기록 가져오기
    // "저번보다 싱크로율이 5% 올랐어요!" 기능을 위해 필요합니다.
    Optional<Session> findFirstByMemberIdAndExerciseIdAndStatusOrderByStartTimeDesc(
            Long memberId, Long exerciseId, Status status
    );

    List<Session> findByMemberIdAndStartTimeBetween(Long memberId, LocalDateTime start, LocalDateTime end);}