package com.shadowfit.service.Exercise;
import com.shadowfit.grpc.ExerciseServiceGrpc;
import com.shadowfit.grpc.PoseDataBatchRequest;
import com.shadowfit.grpc.PoseDataRequest;
import com.shadowfit.grpc.PoseDataResponse;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
@RequiredArgsConstructor
public class ExerciseGrpcService extends ExerciseServiceGrpc.ExerciseServiceImplBase {
    private final PoseDataService poseDataService;

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
}