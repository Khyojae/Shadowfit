package com.shadowfit.integration;

import com.shadowfit.grpc.*;
import com.shadowfit.model.exercise.Exercise;
import com.shadowfit.model.exercise.ExerciseCategory;
import com.shadowfit.model.exercise.PoseData;
import com.shadowfit.model.exercise.Session;
import com.shadowfit.model.exercise.Status;
import com.shadowfit.model.member.Member;
import com.shadowfit.model.member.SelectedPersona;
import com.shadowfit.model.member.UserRole;
import com.shadowfit.repository.exercise.ExercisesRepository;
import com.shadowfit.repository.exercise.PoseDataRepository;
import com.shadowfit.repository.exercise.SessionRepository;
import com.shadowfit.repository.member.MemberRepository;
import com.shadowfit.service.Exercise.ExerciseGrpcService;
import com.shadowfit.service.Exercise.SessionService;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 운동 세션 결합 e2e 통합 테스트.
 *
 * <p>AI 서버는 띄우지 않고, AI → Spring 콜백을 직접 시뮬레이션 (gRPC servicer 메서드를 자바 코드로
 * 직접 호출). Spring 측의 controller·service·repository·동시성·멱등성 흐름을 H2 인메모리 DB 위에서
 * 한 사이클 단위로 검증한다.
 *
 * <p>수동 e2e 절차(카메라·프론트·AI 컨테이너 포함)는 docs/18-testing-guide.md §8 참조.
 */
@SpringBootTest
@Transactional
@DisplayName("운동 세션 결합 e2e — Spring 측 한 사이클 검증")
class ExerciseSessionFlowIntegrationTest {

    @Autowired private ExerciseGrpcService grpcService;
    @Autowired private SessionService sessionService;

    @Autowired private SessionRepository sessionRepository;
    @Autowired private PoseDataRepository poseDataRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ExercisesRepository exercisesRepository;

    private Member testMember;
    private Exercise testExercise;

    @BeforeEach
    void setUp() {
        testMember = memberRepository.saveAndFlush(Member.builder()
                .email("e2e@test.com")
                .username("e2e사용자")
                .password("dummy")
                .preferredUrl("https://youtu.be/dummy")
                .selectedPersona(SelectedPersona.BEGINNER)
                .role(UserRole.USER)
                .build());

        testExercise = exercisesRepository.saveAndFlush(Exercise.builder()
                .name("스쿼트")
                .category(ExerciseCategory.LOWER)
                .expectedDurationMinutes(15)
                .syncThresholdBeginner(new BigDecimal("60.00"))
                .syncThresholdAdvanced(new BigDecimal("85.00"))
                .build());
    }

    /**
     * 새 세션을 IN_PROGRESS 로 저장. 실제 ExerciseAnalysisService.startAnalysis 는
     * @Async + gRPC 호출까지 가지만, 본 테스트는 결합의 *수신측* 만 검증하므로
     * DB 세션 생성만 직접 수행한다.
     */
    private Session createInProgressSession() {
        return sessionRepository.saveAndFlush(Session.builder()
                .member(testMember)
                .exercise(testExercise)
                .referenceSource("https://youtu.be/dummy")
                .startTime(LocalDateTime.now())
                .status(Status.IN_PROGRESS)
                .totalReps(0)
                .difficultyLevel(1)
                .build());
    }

    private PoseDataBatchRequest sampleBatch(Long sessionId, int frameCount, double syncRate) {
        PoseDataBatchRequest.Builder b = PoseDataBatchRequest.newBuilder()
                .setSessionId(sessionId);
        for (int i = 0; i < frameCount; i++) {
            b.addPoseData(PoseDataRequest.newBuilder()
                    .setTimestampSec(i * 0.1)
                    .setJointCoordinates("[{\"index\":11,\"x\":0.5,\"y\":0.5,\"z\":0.0,\"visibility\":1.0}]")
                    .setSyncRate(syncRate)
                    .setFeedbackMessage("자세 양호")
                    .build());
        }
        return b.build();
    }

    private SessionCompleteRequest sampleComplete(Long sessionId, int totalReps, double avg) {
        return SessionCompleteRequest.newBuilder()
                .setSessionId(sessionId)
                .setTotalReps(totalReps)
                .setAvgSyncRate(avg)
                .setMaxSyncRate(avg + 5)
                .setMinSyncRate(avg - 5)
                .setCaloriesBurned(0.0)
                .setDifficultyLevel(1)
                .build();
    }

    @Nested
    @DisplayName("정상 한 사이클")
    class NormalFlow {

        @Test
        @DisplayName("rep 단위 SavePoseDataBatch → CompleteAnalysis 콜백 후 DB 일관 상태")
        void normal_one_cycle() {
            // given: IN_PROGRESS 세션 시작
            Session session = createInProgressSession();
            Long sessionId = session.getId();

            // when 1: rep 1 완성 콜백 (AI → Spring)
            @SuppressWarnings("unchecked")
            StreamObserver<PoseDataResponse> batchObs1 = mock(StreamObserver.class);
            grpcService.savePoseDataBatch(sampleBatch(sessionId, 5, 80.0), batchObs1);

            // when 2: rep 2 완성 콜백
            @SuppressWarnings("unchecked")
            StreamObserver<PoseDataResponse> batchObs2 = mock(StreamObserver.class);
            grpcService.savePoseDataBatch(sampleBatch(sessionId, 4, 75.0), batchObs2);

            // when 3: 세션 종료 콜백
            @SuppressWarnings("unchecked")
            StreamObserver<SessionCompleteResponse> completeObs = mock(StreamObserver.class);
            grpcService.completeAnalysis(sampleComplete(sessionId, 2, 77.5), completeObs);

            // then: gRPC 응답이 정상적으로 전달됨
            verify(batchObs1, times(1)).onNext(any(PoseDataResponse.class));
            verify(batchObs1, times(1)).onCompleted();
            verify(batchObs2, times(1)).onNext(any());

            ArgumentCaptor<SessionCompleteResponse> respCaptor =
                    ArgumentCaptor.forClass(SessionCompleteResponse.class);
            verify(completeObs).onNext(respCaptor.capture());
            verify(completeObs).onCompleted();
            assertThat(respCaptor.getValue().getStatus()).isEqualTo(SessionStatus.COMPLETED);
            assertThat(respCaptor.getValue().getSessionId()).isEqualTo(sessionId);

            // then: pose_data 테이블에 rep1(5행) + rep2(4행) = 9행
            List<PoseData> rows = poseDataRepository.findAll();
            assertThat(rows).hasSize(9);
            assertThat(rows).allSatisfy(p -> {
                assertThat(p.getSession().getId()).isEqualTo(sessionId);
                assertThat(p.getJointCoordinates()).isNotEmpty();
                assertThat(p.getSyncRate()).isBetween(70.0, 85.0);
            });

            // then: 세션 종료 상태로 영속화
            Session finished = sessionRepository.findById(sessionId).orElseThrow();
            assertThat(finished.getStatus()).isEqualTo(Status.COMPLETED);
            assertThat(finished.getTotalReps()).isEqualTo(2);
            assertThat(finished.getAvgSyncRate()).isEqualByComparingTo(new BigDecimal("77.5"));
            assertThat(finished.getEndTime()).isNotNull();
            assertThat(finished.getVersion()).isGreaterThanOrEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("멱등성 — At-Least-Once 콜백 대응")
    class Idempotency {

        @Test
        @DisplayName("CompleteAnalysis가 두 번 도착해도 첫 결과·endTime 보존, version 추가 증가 없음")
        void duplicate_complete_callback_is_noop() {
            // given
            Session session = createInProgressSession();
            Long sessionId = session.getId();

            // when 1: 첫 콜백
            @SuppressWarnings("unchecked")
            StreamObserver<SessionCompleteResponse> obs1 = mock(StreamObserver.class);
            grpcService.completeAnalysis(sampleComplete(sessionId, 3, 80.0), obs1);

            Session afterFirst = sessionRepository.findById(sessionId).orElseThrow();
            LocalDateTime firstEndTime = afterFirst.getEndTime();
            Long firstVersion = afterFirst.getVersion();
            assertThat(afterFirst.getStatus()).isEqualTo(Status.COMPLETED);
            assertNotNull(firstEndTime);

            // when 2: 같은 sessionId 로 재전송 (값은 약간 달라도 무시되어야 함)
            @SuppressWarnings("unchecked")
            StreamObserver<SessionCompleteResponse> obs2 = mock(StreamObserver.class);
            grpcService.completeAnalysis(sampleComplete(sessionId, 99, 99.9), obs2);

            // then: 첫 결과 그대로 보존
            Session afterSecond = sessionRepository.findById(sessionId).orElseThrow();
            assertThat(afterSecond.getStatus()).isEqualTo(Status.COMPLETED);
            assertThat(afterSecond.getTotalReps()).isEqualTo(3); // 99 로 덮이지 않음
            assertThat(afterSecond.getAvgSyncRate()).isEqualByComparingTo(new BigDecimal("80.0"));
            assertThat(afterSecond.getEndTime()).isEqualTo(firstEndTime);
            assertThat(afterSecond.getVersion()).isEqualTo(firstVersion); // version 증가 없음
        }
    }

    @Nested
    @DisplayName("타임아웃 후 늦은 콜백 — FAILED → COMPLETED 덮어쓰기")
    class LateCallbackAfterTimeout {

        @Test
        @DisplayName("스케줄러가 FAILED 처리한 후에도 AI 콜백이 도착하면 COMPLETED 로 덮어씀")
        void late_callback_overwrites_failed() {
            // given: IN_PROGRESS → 스케줄러가 FAILED 로 떨어뜨린 상태
            Session session = createInProgressSession();
            Long sessionId = session.getId();
            boolean failed = sessionService.markAsFailedIfStillInProgress(
                    sessionId, LocalDateTime.now());
            assertThat(failed).isTrue();
            assertThat(sessionRepository.findById(sessionId).orElseThrow().getStatus())
                    .isEqualTo(Status.FAILED);

            // when: 늦게 도착한 AI 완료 콜백
            @SuppressWarnings("unchecked")
            StreamObserver<SessionCompleteResponse> obs = mock(StreamObserver.class);
            grpcService.completeAnalysis(sampleComplete(sessionId, 5, 82.0), obs);

            // then: COMPLETED 로 덮여쓰임, 운동 결과 유실 X
            Session result = sessionRepository.findById(sessionId).orElseThrow();
            assertThat(result.getStatus()).isEqualTo(Status.COMPLETED);
            assertThat(result.getTotalReps()).isEqualTo(5);
            assertThat(result.getAvgSyncRate()).isEqualByComparingTo(new BigDecimal("82.0"));
        }
    }

    @Nested
    @DisplayName("세션 없을 때 콜백 — 안전 처리")
    class CallbackForUnknownSession {

        @Test
        @DisplayName("존재하지 않는 sessionId 로 SavePoseDataBatch → 에러 응답, DB 변경 없음")
        void save_batch_for_unknown_session_errors_safely() {
            long unknownId = 9999L;
            @SuppressWarnings("unchecked")
            StreamObserver<PoseDataResponse> obs = mock(StreamObserver.class);

            grpcService.savePoseDataBatch(sampleBatch(unknownId, 3, 70.0), obs);

            // SESSION_NOT_FOUND → catch → onError 호출
            verify(obs, times(1)).onError(any(Throwable.class));
            assertThat(poseDataRepository.count()).isZero();
        }
    }
}
