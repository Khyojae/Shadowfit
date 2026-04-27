package com.shadowfit.global.config;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InternalAuthInterceptor implements ServerInterceptor {
    @Value("${internal.api.token}")
    private String internalToken;

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, // 현재 들어온 호출 정보
            Metadata headers,              // 4. 요청에 담긴 '헤더'(신분증이 들어있는 주머니)
            io.grpc.ServerCallHandler<ReqT, RespT> nextt) { // 5. 다음 단계로 보내주는 '안내자' (현재 nextt로 되어있음)

        // 6. 헤더에서 "Authorization"이라는 이름표를 찾겠다는 기준을 만듭니다.
        Metadata.Key<String> authKey = Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

        // 7. 실제로 헤더 주머니에서 그 이름표를 꺼내서 값을 확인합니다. (예: Bearer mytoken123)
        String authHeader = headers.get(authKey);

        // 8. [검문 실시] 토큰이 없거나, 우리가 가진 비밀번호와 다르면?
        if (authHeader == null || !authHeader.equals("Bearer " + internalToken)) {
            // 9. "너 누구야!" 하고 연결을 즉시 끊어버립니다. (UNAUTHENTICATED)
            call.close(Status.UNAUTHENTICATED.withDescription("유효하지 않은 토큰"), new Metadata());

            // 빈 리스너를 돌려주어 뒷단 로직이 실행되지 않게 막습니다.
            return new ServerCall.Listener<ReqT>() {};
        }

        // 10. [통과] 신분증이 확실하면, 안내자(nextt)에게 "다음 로직(Service)으로 보내줘"라고 시킵니다.
        return nextt.startCall(call, headers);
    }

}
