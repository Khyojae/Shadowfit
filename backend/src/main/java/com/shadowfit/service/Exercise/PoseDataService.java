package com.shadowfit.service.Exercise;

import com.shadowfit.dto.exercises.PoseDataRequestDto;
import com.shadowfit.model.exercise.PoseData;
import com.shadowfit.model.exercise.Session;
import com.shadowfit.repository.PoseDataRepository;
import com.shadowfit.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PoseDataService {
    private final PoseDataRepository poseDataRepository;
    private final SessionRepository sessionRepository;

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
}