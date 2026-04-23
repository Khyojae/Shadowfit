package com.shadowfit.controller;

import com.shadowfit.dto.exercises.session.ExercisesResponseDto;
import com.shadowfit.dto.exercises.VideoRequestDto;
import com.shadowfit.global.security.auth.CustomUserDetails;
import com.shadowfit.model.exercise.Status;
import com.shadowfit.service.Exercise.ExerciseAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.shadowfit.dto.exercises.session.SessionUpdateRequestDto;
import com.shadowfit.dto.exercises.session.SessionUpdateResponseDto;

import java.time.LocalDateTime;

@Tag(name = "운동 분석", description = "운동 분석 및 세션 관리 API")
@Slf4j
@RestController
@RequestMapping("/exercises")
@RequiredArgsConstructor
public class ExercisesController {

    private final ExerciseAnalysisService analysisService;

    /**
     * ✅ 운동 세션 시작 (핵심 API)
     * App → Spring → gRPC → FastAPI 흐름 시작점
     */
    @Operation(summary="운동 세션 시작",description = "운동을 시작할 수 있음/ ai서버에서 특정 조건을 달성하면 운동 종료가됨")
    @PostMapping("/sessions")
    public ResponseEntity<ExercisesResponseDto> startAnalysis(
            @RequestBody VideoRequestDto dto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long memberId = userDetails.getMember().getId();

        log.info("운동 분석 요청 시작 - userId: {}, exerciseId: {}",
                memberId, dto.getExerciseId());

        // 서비스 호출 (내부에서 gRPC 호출까지 이어짐)
        Long sessionId = analysisService.startAnalysis(dto, memberId);

        // 응답 DTO 생성
        ExercisesResponseDto response = ExercisesResponseDto.builder()
                .sessionId(sessionId.intValue()) // 👉 Long → Integer 변환 (DTO 맞춤)
                .exerciseId(dto.getExerciseId().intValue())
                .startTime(LocalDateTime.now())
                .status(Status.IN_PROGRESS)
                .build();

        return ResponseEntity.accepted().body(response);
    }

    /**
     * ✅ 기준 좌표 추출 (관리자/등록용)
     */
    @Operation(summary="기준 좌표 추출",description = "기준 좌표 추출 요청을 할 수 있음")
    @PostMapping("/{exerciseId}/reference")
    public ResponseEntity<String> extractReference(
            @PathVariable Long exerciseId,
            @RequestBody String youtubeUrl
    ) {
        log.info("기준 좌표 추출 요청 - exerciseId: {}", exerciseId);

        analysisService.extractReferencePoses(exerciseId, youtubeUrl);

        return ResponseEntity.accepted()
                .body("기준 좌표 추출 요청이 전달되었습니다.");
    }

    /**
     * ✅ 운동 세션 종료 및 결과 저장
     * App에서 측정한 최종 데이터를 받아 DB를 업데이트함
     */
    @Operation(summary="운동 세션 종료 요청",description = "프론트에서 운동 종료를 누르면 종료가됨")
    @PutMapping("/sessions/{sessionId}/complete")
    public ResponseEntity<SessionUpdateResponseDto> completeSession(
            @PathVariable Long sessionId,
            @RequestBody SessionUpdateRequestDto updateDto
    ) {
        log.info("운동 세션 종료 요청 - sessionId: {}, totalReps: {}",
                sessionId, updateDto.getTotalReps());

        // 서비스 호출하여 상태 변경 및 결과 업데이트
        analysisService.completeSession(sessionId, updateDto);

        // 응답 생성
        SessionUpdateResponseDto response = SessionUpdateResponseDto.builder()
                .sessionId(sessionId.intValue())
                .status(Status.COMPLETED)
                .endTime(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(response);
    }




}