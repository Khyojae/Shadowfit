package com.shadowfit.service.Exercise;
import com.shadowfit.grpc.ExerciseServiceGrpc;
import com.shadowfit.grpc.PoseDataBatchRequest;
import com.shadowfit.grpc.PoseDataRequest;
import com.shadowfit.grpc.PoseDataResponse;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.shadowfit.grpc.*;
import net.devh.boot.grpc.server.service.GrpcService;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class ExerciseGrpcService extends ExerciseServiceGrpc.ExerciseServiceImplBase {
    private final PoseDataService poseDataService;
    private final SessionService sessionService;

    /**
     * [AI -> Spring] 운동 분석 중 생성된 포즈 데이터들을 배치(Batch)로 저장합니다.
     * 분석 도중 발생하는 방대한 좌표 데이터를 효율적으로 DB에 기록합니다.
     */
    @Override
    public void savePoseDataBatch(PoseDataBatchRequest request, StreamObserver<PoseDataResponse> responseObserver) {
        try {
            poseDataService.savePoseDataBatchGrpc(request);

            PoseDataRequest lastData = request.getPoseData(request.getPoseDataCount() - 1);
            PoseDataResponse response = PoseDataResponse.newBuilder().setSuccess(true).
                    setSessionId(request.getSessionId())
                    .setTimestampSec(lastData.getTimestampSec())
                    .setJointCoordinates(lastData.getJointCoordinates())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    /**
     * [AI -> Spring] 유튜브 영상에서 추출된 '정석 기준 좌표' 전체를 수신하여 저장합니다.
     * 운동 종목 등록 시 AI 서버가 추출한 좌표 데이터를 DB에 영속화하는 역할을 합니다.
     */
    @Override
    public void extractReferenceData(com.shadowfit.grpc.ExtractRequest request,
                                     io.grpc.stub.StreamObserver<com.shadowfit.grpc.ExtractResponse> responseObserver) {
        try {
            log.info("기준 좌표 추출 데이터 수신 시작 - 운동 ID: {}", request.getExerciseId());

            poseDataService.saveReferencePoses(request.getExerciseId(), request.getExtractedPosesList());

            // [수정] 변수 이름을 'response' 대신 'extractResponse'로 바꿉니다.
            ExtractResponse extractResponse = ExtractResponse.newBuilder()
                    .setSuccess(true)
                    .setExerciseId(request.getExerciseId())
                    .build();

            responseObserver.onNext(extractResponse);
            responseObserver.onCompleted();
            log.info("기준 좌표 저장 완료 - 운동 ID: {}", request.getExerciseId());
        } catch (Exception e) {
            log.error("기준 좌표 저장 중 에러: {}", e.getMessage());
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("DB 저장 실패: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    /**
     * [AI -> Spring] 최종 분석 완료 보고 (핵심 종료 지점)
     * AI 서버가 모든 연산을 마치고 최종 결과(횟수, 일치율 등)를 스프링에 전달할 때 호출됩니다.
     */
    @Override
    public void completeAnalysis(com.shadowfit.grpc.SessionCompleteRequest request,
                                 io.grpc.stub.StreamObserver<com.shadowfit.grpc.SessionCompleteResponse> responseObserver) {
        try {
            // AI 서버가 보내온 gRPC 데이터를 SessionService를 통해 DB에 반영
            sessionService.completeSession(request);

            SessionCompleteResponse response = SessionCompleteResponse.newBuilder()
                    .setSessionId(request.getSessionId())
                    .setStatus(com.shadowfit.grpc.SessionStatus.COMPLETED)
                    .setEndTime(com.google.protobuf.util.Timestamps.fromMillis(System.currentTimeMillis()))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
            log.info("AI 서버 gRPC에 의한 세션 종료 성공 - 세션 ID: {}", request.getSessionId());
        } catch (Exception e) {
            log.error("세션 종료 gRPC 처리 중 에러: {}", e.getMessage());
            responseObserver.onError(e);
        }
    }
}