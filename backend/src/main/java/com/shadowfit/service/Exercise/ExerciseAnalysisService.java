package com.shadowfit.service.Exercise;

import com.shadowfit.dto.exercises.FastApiRequestDto;
import com.shadowfit.dto.exercises.VideoRequestDto;
import com.shadowfit.dto.exercises.session.SessionUpdateRequestDto;
import com.shadowfit.global.error.BusinessException;
import com.shadowfit.global.error.ErrorCode;
import com.shadowfit.global.util.YoutubeValidator;
import com.shadowfit.grpc.*;
import com.shadowfit.model.exercise.Exercise;
import com.shadowfit.model.exercise.ExerciseReference;
import com.shadowfit.model.exercise.Session;
import com.shadowfit.model.exercise.Status;
import com.shadowfit.model.member.Member;
import com.shadowfit.repository.ExerciseReferenceRepository;
import com.shadowfit.repository.ExercisesRepository;
import com.shadowfit.repository.MemberRepository;
import com.shadowfit.repository.SessionRepository;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExerciseAnalysisService {
    private final WebClient webClient;
    private final SessionRepository sessionRepository;
    private final ExercisesRepository exercisesRepository;
    private final MemberRepository memberRepository;
    private final SessionService sessionService;
    private final ExerciseReferenceRepository referenceRepository;

    @Value("${internal.api.token}")
    private String internalToken;

    @GrpcClient("fastapi-client")
    private ExerciseServiceGrpc.ExerciseServiceStub exerciseAsyncStub;

    /**
     * [STEP 1: 기준 데이터 등록]
     * 사용자가 선택한 유튜브 URL에서 AI가 스켈레톤 좌표를 추출하도록 요청합니다.
     */
    public void extractReferencePoses(Long exerciseId, String youtubeUrl) {
        com.shadowfit.grpc.ExtractRequest request = com.shadowfit.grpc.ExtractRequest.newBuilder()
                .setExerciseId(exerciseId)
                .setYoutubeUrl(youtubeUrl)
                .build();

        log.info("FastAPI에게 기준 좌표 추출 요청 전송 - 운동 ID: {}", exerciseId);

        exerciseAsyncStub.extractReferenceData(request, new StreamObserver<com.shadowfit.grpc.ExtractResponse>() {
            @Override
            public void onNext(com.shadowfit.grpc.ExtractResponse value) {
                log.info("FastAPI 추출 시작 응답 수신 - 운동 ID: {}", value.getExerciseId());
            }
            @Override
            public void onError(Throwable t) {
                log.error("좌표 추출 gRPC 통신 장애: {}", t.getMessage());
            }
            @Override
            public void onCompleted() {
                log.info("좌표 추출 gRPC 요청 완료");
            }
        });
    }

    /**
     * [STEP 2: 운동 분석 시작 - Entry Point]
     * 앱의 요청을 받아 DB에 세션을 생성하고 즉시 세션 ID를 반환합니다. (응답 속도 최적화)
     */
    @Transactional
    public Long startAnalysis(VideoRequestDto appDto, Long currentMemberId) {
        Session savedSession = sessionService.createSession(appDto, currentMemberId);
        Long sessionId = savedSession.getId();

        // 비동기로 FastAPI에 분석 요청
        this.sendAnalysisRequestToFastApi(sessionId, appDto);

        return sessionId;
    }

    /**
     * [STEP 3: 비동기 gRPC 데이터 전송]
     * DB에서 기준 좌표(Reference)를 조회하여 FastAPI 서버로 전송합니다.
     */
    @Async
    @Transactional(readOnly = true)
    public void sendAnalysisRequestToFastApi(Long sessionId, VideoRequestDto appDto) {
        log.info("비동기 분석 요청 시작 - 세션 ID: {}", sessionId);

        List<ExerciseReference> referencePoses = referenceRepository.findByExerciseId(appDto.getExerciseId());

        AnalyzeRequest.Builder requestBuilder = AnalyzeRequest.newBuilder()
                .setExerciseId(appDto.getExerciseId())
                .setSessionId(sessionId)
                .setReferenceSource(YoutubeValidator.extractId(appDto.getReferenceSource()));

        for (ExerciseReference ref : referencePoses) {
            requestBuilder.addReferencePoses(PoseDataRequest.newBuilder()
                    .setTimestampSec(ref.getTimestampSec())
                    .setJointCoordinates(ref.getJointCoordinates())
                    .build());
        }

        exerciseAsyncStub.startAnalysis(requestBuilder.build(), new StreamObserver<AnalyzeResponse>() {
            @Override
            public void onNext(AnalyzeResponse value) {
                log.info("FastAPI 응답 수신 - 세션: {}", value.getSessionId());
            }
            @Override
            public void onError(Throwable t) {
                log.error("gRPC 통신 장애: {}", t.getMessage());
            }
            @Override
            public void onCompleted() {
                log.info("FastAPI 전송 완료");
            }
        });
    }

    /**
     * [STEP 4: 사용자 강제 중단]
     * 사용자가 앱에서 종료를 눌렀을 때 AI 서버의 연산 스레드를 중단시키기 위한 신호를 보냅니다.
     */
    public void stopAnalysis(Long sessionId) {
        log.info("AI 서버 분석 중단 요청 전송 - sessionId: {}", sessionId);

        com.shadowfit.grpc.StopRequest request = com.shadowfit.grpc.StopRequest.newBuilder()
                .setSessionId(sessionId.intValue())
                .build();

        exerciseAsyncStub.stopAnalysis(request, new io.grpc.stub.StreamObserver<com.shadowfit.grpc.StopResponse>() {
            @Override
            public void onNext(com.shadowfit.grpc.StopResponse value) {
                log.info("AI 서버 응답: {}", value.getMessage());
            }
            @Override
            public void onError(Throwable t) {
                log.error("AI 서버 중단 실패: {}", t.getMessage());
            }
            @Override
            public void onCompleted() {}
        });
    }

    /**
     * [STEP 5: 분석 결과 영속화 (Callback)]
     * AI 서버가 분석을 마치고 gRPC로 보고해온 최종 결과를 DB에 반영합니다.
     */
    @Transactional
    public void completeSession(Long sessionId, SessionUpdateRequestDto dto) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));

        session.setTotalReps(dto.getTotalReps());
        session.setAvgSyncRate(java.math.BigDecimal.valueOf(dto.getAvgSyncRate()));
        session.setStatus(Status.COMPLETED);
        session.setEndTime(LocalDateTime.now());

        sessionRepository.save(session);
        log.info("세션 {} DB 업데이트 완료", sessionId);
    }

}