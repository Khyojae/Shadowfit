package com.shadowfit.service.Exercise;

import com.shadowfit.dto.exercises.feedback.FeedbackTemplateDto;
import com.shadowfit.model.exercise.ExerciseFeedbackTemplate;
import com.shadowfit.model.exercise.FeedbackType;
import com.shadowfit.model.member.SelectedPersona;
import com.shadowfit.repository.exercise.ExerciseFeedbackTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FeedbackTemplateService {
    private final ExerciseFeedbackTemplateRepository templateRepository;

    /**
     * 페르소나 row 가 있는 feedback_type 은 그것, 없는 것은 persona IS NULL fallback (분기 4-A).
     * priority ASC 정렬. persona null 호출 시 fallback 전체 반환.
     */
    public List<FeedbackTemplateDto> getTemplatesByExercise(Long exerciseId, SelectedPersona persona) {
        List<ExerciseFeedbackTemplate> fallback =
                templateRepository.findByExerciseIdAndPersonaIsNullOrderByPriorityAsc(exerciseId);

        Map<FeedbackType, ExerciseFeedbackTemplate> merged = new HashMap<>();
        for (ExerciseFeedbackTemplate t : fallback) {
            merged.put(t.getFeedbackType(), t);
        }
        if (persona != null) {
            List<ExerciseFeedbackTemplate> personaTemplates =
                    templateRepository.findByExerciseIdAndPersonaOrderByPriorityAsc(exerciseId, persona);
            for (ExerciseFeedbackTemplate t : personaTemplates) {
                merged.put(t.getFeedbackType(), t);
            }
        }

        return merged.values().stream()
                .sorted(Comparator.comparing(ExerciseFeedbackTemplate::getPriority))
                .map(FeedbackTemplateDto::fromEntity)
                .toList();
    }
}