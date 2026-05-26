package com.shadowfit.repository.exercise;

import com.shadowfit.model.exercise.ExerciseFeedbackTemplate;
import com.shadowfit.model.exercise.FeedbackType;
import com.shadowfit.model.member.SelectedPersona;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExerciseFeedbackTemplateRepository extends JpaRepository<ExerciseFeedbackTemplate, Long> {

    List<ExerciseFeedbackTemplate> findByExerciseIdOrderByPriorityAsc(Long exerciseId);

    Optional<ExerciseFeedbackTemplate> findByExerciseIdAndFeedbackType(Long exerciseId, FeedbackType feedbackType);

    /** 특정 페르소나에 매칭되는 row 만 (FeedbackTemplateService 가 fallback 과 merge). */
    List<ExerciseFeedbackTemplate> findByExerciseIdAndPersonaOrderByPriorityAsc(
            Long exerciseId, SelectedPersona persona);

    /** persona IS NULL 의 fallback row (페르소나 row 없을 때 사용). */
    List<ExerciseFeedbackTemplate> findByExerciseIdAndPersonaIsNullOrderByPriorityAsc(Long exerciseId);
}