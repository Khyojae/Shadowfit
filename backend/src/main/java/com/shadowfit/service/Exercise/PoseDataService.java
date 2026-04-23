package com.shadowfit.service.Exercise;

import com.shadowfit.dto.exercises.PoseDataRequestDto;
import com.shadowfit.global.error.BusinessException;
import com.shadowfit.global.error.ErrorCode;
import com.shadowfit.grpc.PoseDataBatchRequest;
import com.shadowfit.grpc.PoseDataRequest;
import com.shadowfit.model.exercise.Exercise;
import com.shadowfit.model.exercise.ExerciseReference;
import com.shadowfit.model.exercise.PoseData;
import com.shadowfit.model.exercise.Session;
import com.shadowfit.repository.ExerciseReferenceRepository;
import com.shadowfit.repository.ExercisesRepository;
import com.shadowfit.repository.PoseDataRepository;
import com.shadowfit.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PoseDataService {
    private final PoseDataRepository poseDataRepository;
    private final SessionRepository sessionRepository;
    private final ExercisesRepository exercisesRepository;
    private final ExerciseReferenceRepository referenceRepository;

    /**
     * [HTTP 방식] 포즈 데이터 배치 저장
     * WebClient 등을 통해 리스트 형태로 넘어온 좌표 데이터를 한꺼번에 저장합니다.
     */
    @Transactional
    public void savePoseDataBatch(List<PoseDataRequestDto> dtos) {
        if (dtos.isEmpty()) return;

        // 1. 세션 정보 조회 (리스트의 첫 번째 세션 ID 기준)
        Long sessionId = dtos.get(0).getSessionId();
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 세션입니다. ID: " + sessionId));

        // 2. DTO 리스트를 PoseData 엔티티 리스트로 한 번에 변환
        List<PoseData> entities = dtos.stream()
                .map(dto -> PoseData.builder()
                        .session(session)
                        .timestampSec(dto.getTimestampSec())
                        .jointCoordinates(dto.getJointCoordinates())
                        .build())
                .collect(Collectors.toList());

        // 3. 배치 저장 실행
        poseDataRepository.saveAll(entities);
    }

    /**
     * [gRPC 방식] 기준 좌표(Reference) 저장
     * AI 서버가 추출한 운동 종목별 '정석 포즈' 좌표들을 DB에 저장합니다.
     */
    @Transactional
    public void saveReferencePoses(Long exerciseId, List<PoseDataRequest> poseDataList) {
        // 1. 해당 운동이 존재하는지 확인
        Exercise exercise = exercisesRepository.findById(exerciseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXERCISE_NOT_FOUND));

        // 2. 기존에 저장된 기준 좌표가 있다면 삭제 (선택 사항: 업데이트 개념이라면 삭제 후 재입력)
        // referenceRepository.deleteByExercise(exercise);

        // 3. Proto 메시지 리스트 -> ExerciseReference 엔티티 리스트 변환
        List<ExerciseReference> entities = poseDataList.stream()
                .map(p -> ExerciseReference.builder()
                        .exercise(exercise)
                        .timestampSec(p.getTimestampSec())
                        .jointCoordinates(p.getJointCoordinates())
                        .build())
                .collect(Collectors.toList());

        // 4. 저장 (referenceRepository는 ExerciseReferenceRepository 주입 필요)
        referenceRepository.saveAll(entities);
    }

    /**
     * [gRPC 방식] 실시간 분석 좌표 배치 저장
     * AI 서버에서 주기적으로 쏴주는 분석 좌표들을 비동기(@Async)로 처리하여
     * 메인 통신 흐름에 지장을 주지 않고 DB에 저장합니다.
     */
    @Async
    @Transactional
    public void savePoseDataBatchGrpc( PoseDataBatchRequest request) {
        if (request.getPoseDataCount() == 0) return;

        // 1. 세션 조회
        Session session = sessionRepository.findById(request.getSessionId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 세션: " + request.getSessionId()));

        // 2. Proto 메시지 리스트 -> Entity 리스트 변환
        List<PoseData> entities = request.getPoseDataList().stream()
                .map(p -> PoseData.builder()
                        .session(session)
                        .timestampSec(p.getTimestampSec())
                        .jointCoordinates(p.getJointCoordinates())
                        .build())
                .collect(Collectors.toList());

        // 3. 저장
        poseDataRepository.saveAll(entities);
    }
}