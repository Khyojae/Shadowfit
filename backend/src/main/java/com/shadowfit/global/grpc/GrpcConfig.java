package com.shadowfit.global.grpc;

import com.shadowfit.grpc.ExerciseServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class GrpcConfig {
    @Value("${grpc.client.fastapi.address}")
    private String fastapiAddress;

    @Bean
    public ExerciseServiceGrpc.ExerciseServiceBlockingStub exerciseServiceStub() {
        // FastAPI 서버 주소로 채널 생성
        ManagedChannel channel = ManagedChannelBuilder.forTarget(fastapiAddress)
                .usePlaintext() // 보안 설정(SSL/TLS)이 없으면 plaintext 사용
                .build();

        return ExerciseServiceGrpc.newBlockingStub(channel);
    }
}
