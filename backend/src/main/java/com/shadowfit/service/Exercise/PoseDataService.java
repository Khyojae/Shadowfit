package com.shadowfit.service.Exercise;

import com.shadowfit.dto.exercises.PoseDataRequestDto;
import com.shadowfit.global.error.BusinessException;
import com.shadowfit.global.error.ErrorCode;
import com.shadowfit.grpc.PoseDataRequest;
import com.shadowfit.model.exercise.Exercise;
import com.shadowfit.model.exercise.ExerciseReference;
import com.shadowfit.model.exercise.PoseData;
import com.shadowfit.model.exercise.Session;
import com.shadowfit.repository.exercise.ExerciseReferenceRepository;
import com.shadowfit.repository.exercise.ExercisesRepository;
import com.shadowfit.repository.exercise.PoseDataRepository;
import com.shadowfit.repository.exercise.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PoseDataService {

    private final PoseDataRepository poseDataRepository;
    private final SessionRepository sessionRepository;
    private final ExercisesRepository exercisesRepository;
    private final ExerciseReferenceRepository referenceRepository;

    /**
     * [실시간 저장] FastAPI가 주기적으로 쏴주는 분석 좌표 데이터 묶음을 DB에 저장합니다.
     */
    @Transactional
    public void savePoseDataBatch(Long sessionId, List<com.shadowfit.grpc.PoseDataRequest> grpcList) {
        if (grpcList == null || grpcList.isEmpty()) return;

        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));

        List<PoseData> entities = grpcList.stream()
                .map(grpc -> PoseData.builder()
                        .session(session)
                        .timestampSec(grpc.getTimestampSec())
                        .jointCoordinates(grpc.getJointCoordinates())
                        .syncRate(grpc.getSyncRate())
                        .isCorrect(grpc.getSyncRate() >= 40.0) // 40점 기준 (수정 가능)
                        .feedbackMessage(grpc.getFeedbackMessage())
                        .build())
                .collect(Collectors.toList());

        poseDataRepository.saveAll(entities);
        log.info("세션 {} : 포즈 데이터 {}개 일괄 저장 성공", sessionId, entities.size());
    }

    /**
     * [실시간 저장 - REST] FastAPI가 HTTP로 보내는 좌표 DTO 묶음을 DB에 저장합니다.
     * 동일 batch에 여러 sessionId가 섞여 있어도 세션별로 그룹화해 처리합니다.
     */
    @Transactional
    public void savePoseDataBatch(List<PoseDataRequestDto> dtos) {
        if (dtos == null || dtos.isEmpty()) return;

        Map<Long, List<PoseDataRequestDto>> bySession = dtos.stream()
                .filter(d -> d.getSessionId() != null)
                .collect(Collectors.groupingBy(PoseDataRequestDto::getSessionId));

        List<PoseData> entities = new ArrayList<>();
        for (Map.Entry<Long, List<PoseDataRequestDto>> entry : bySession.entrySet()) {
            Long sessionId = entry.getKey();
            Session session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));

            for (PoseDataRequestDto dto : entry.getValue()) {
                entities.add(PoseData.builder()
                        .session(session)
                        .timestampSec(dto.getTimestampSec())
                        .jointCoordinates(dto.getJointCoordinates())
                        .build());
            }
        }

        poseDataRepository.saveAll(entities);
        log.info("REST 포즈 데이터 {}개 저장 (세션 {}개)", entities.size(), bySession.size());
    }

    /**
     * [관리자용] AI가 유튜브에서 추출한 '정석 기준 좌표'를 DB에 저장합니다.
     */
    @Transactional
    public void saveReferencePoses(Long exerciseId, List<com.shadowfit.grpc.PoseDataRequest> grpcList) {
        if (grpcList == null || grpcList.isEmpty()) return;

        Exercise exercise = exercisesRepository.findById(exerciseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXERCISE_NOT_FOUND));

        List<ExerciseReference> referenceEntities = grpcList.stream()
                .map(grpc -> ExerciseReference.builder()
                        .exercise(exercise)
                        .timestampSec(grpc.getTimestampSec())
                        .jointCoordinates(grpc.getJointCoordinates())
                        .build())
                .collect(Collectors.toList());

        referenceRepository.saveAll(referenceEntities);
        log.info("운동 ID {} : 기준 좌표 {}개 등록 완료", exerciseId, referenceEntities.size());
    }
}