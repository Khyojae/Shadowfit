package com.shadowfit.service.Exercise;

import com.shadowfit.model.exercise.*;
import com.shadowfit.model.member.Member;
import com.shadowfit.repository.exercise.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * SessionTimeoutScheduler 단위 테스트
 *
 * 네트워크 타임아웃 처리 로직 + FastAPI 완료 콜백과의 동시성(낙관적 락) 시나리오를 검증합니다.
 */
@DisplayName("세션 타임아웃 스케줄러 테스트")
class SessionTimeoutSchedulerTest {

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private SessionService sessionService;

    private SessionTimeoutScheduler scheduler;

    private Exercise testExercise;
    private Member testMember;
    private Session testSession;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        scheduler = new SessionTimeoutScheduler(sessionRepository, sessionService, 30);

        testMember = Member.builder()
                .id(1L)
                .email("test@test.com")
                .username("테스트유저")
                .build();

        testExercise = Exercise.builder()
                .id(1L)
                .name("스쿼트")
                .category(ExerciseCategory.LOWER)
                .expectedDurationMinutes(15)
                .syncThresholdBeginner(new BigDecimal("60.00"))
                .syncThresholdAdvanced(new BigDecimal("85.00"))
                .build();

        testSession = Session.builder()
                .id(1L)
                .member(testMember)
                .exercise(testExercise)
                .startTime(LocalDateTime.now().minusMinutes(50))
                .status(Status.IN_PROGRESS)
                .totalReps(0)
                .difficultyLevel(1)
                .build();
    }

    @Test
    @DisplayName("타임아웃된 세션은 SessionService.markAsFailed 호출로 FAILED 처리되어야 함")
    void testTimeoutSessionMarkedFailed() {
        when(sessionRepository.findByStatus(Status.IN_PROGRESS))
                .thenReturn(List.of(testSession));
        when(sessionService.markAsFailedIfStillInProgress(eq(1L), any(LocalDateTime.class)))
                .thenReturn(true);

        scheduler.checkAndTimeoutSessions();

        verify(sessionService, times(1))
                .markAsFailedIfStillInProgress(eq(1L), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("타임아웃되지 않은 세션은 markAsFailed 호출되지 않아야 함")
    void testNonTimeoutSessionNotCalled() {
        Session recentSession = Session.builder()
                .id(2L)
                .member(testMember)
                .exercise(testExercise)
                .startTime(LocalDateTime.now().minusMinutes(5))
                .status(Status.IN_PROGRESS)
                .totalReps(0)
                .difficultyLevel(1)
                .build();

        when(sessionRepository.findByStatus(Status.IN_PROGRESS))
                .thenReturn(List.of(recentSession));

        scheduler.checkAndTimeoutSessions();

        verify(sessionService, never())
                .markAsFailedIfStillInProgress(any(), any());
    }

    @Test
    @DisplayName("IN_PROGRESS 세션이 없으면 SessionService를 호출하지 않아야 함")
    void testNoInProgressSessionsDoesNothing() {
        when(sessionRepository.findByStatus(Status.IN_PROGRESS))
                .thenReturn(new ArrayList<>());

        scheduler.checkAndTimeoutSessions();

        verify(sessionService, never())
                .markAsFailedIfStillInProgress(any(), any());
    }

    @Test
    @DisplayName("운동별 예상시간에 따라 타임아웃을 다르게 적용해야 함")
    void testTimeoutBasedOnExerciseDuration() {
        // 예상시간 30분 + 버퍼 30분 = 60분 임계, 50분 경과 → 타임아웃 아님
        Exercise longExercise = Exercise.builder()
                .id(2L)
                .name("마라톤훈련")
                .category(ExerciseCategory.FULL)
                .expectedDurationMinutes(30)
                .build();

        Session longSession = Session.builder()
                .id(3L)
                .member(testMember)
                .exercise(longExercise)
                .startTime(LocalDateTime.now().minusMinutes(50))
                .status(Status.IN_PROGRESS)
                .build();

        when(sessionRepository.findByStatus(Status.IN_PROGRESS))
                .thenReturn(List.of(longSession));

        scheduler.checkAndTimeoutSessions();

        verify(sessionService, never())
                .markAsFailedIfStillInProgress(any(), any());
    }

    @Test
    @DisplayName("예상시간 10분인 운동은 41분 경과 시 타임아웃되어야 함")
    void testQuickExerciseTimeoutAfter40Minutes() {
        Exercise quickExercise = Exercise.builder()
                .id(3L)
                .name("플랭크")
                .category(ExerciseCategory.CORE)
                .expectedDurationMinutes(10)
                .build();

        Session quickSession = Session.builder()
                .id(4L)
                .member(testMember)
                .exercise(quickExercise)
                .startTime(LocalDateTime.now().minusMinutes(41))
                .status(Status.IN_PROGRESS)
                .build();

        when(sessionRepository.findByStatus(Status.IN_PROGRESS))
                .thenReturn(List.of(quickSession));
        when(sessionService.markAsFailedIfStillInProgress(eq(4L), any(LocalDateTime.class)))
                .thenReturn(true);

        scheduler.checkAndTimeoutSessions();

        verify(sessionService, times(1))
                .markAsFailedIfStillInProgress(eq(4L), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("[동시성] FastAPI 완료와 충돌 시 OptimisticLockException을 양보하고 다른 세션 처리는 계속해야 함")
    void testYieldOnOptimisticLockConflict() {
        // 두 세션 모두 타임아웃 대상이지만 첫 번째는 충돌, 두 번째는 정상
        Session conflictingSession = Session.builder()
                .id(10L)
                .member(testMember)
                .exercise(testExercise)
                .startTime(LocalDateTime.now().minusMinutes(50))
                .status(Status.IN_PROGRESS)
                .build();

        Session normalSession = Session.builder()
                .id(11L)
                .member(testMember)
                .exercise(testExercise)
                .startTime(LocalDateTime.now().minusMinutes(50))
                .status(Status.IN_PROGRESS)
                .build();

        when(sessionRepository.findByStatus(Status.IN_PROGRESS))
                .thenReturn(List.of(conflictingSession, normalSession));

        when(sessionService.markAsFailedIfStillInProgress(eq(10L), any(LocalDateTime.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Session.class, 10L));
        when(sessionService.markAsFailedIfStillInProgress(eq(11L), any(LocalDateTime.class)))
                .thenReturn(true);

        // 충돌이 발생해도 예외가 외부로 전파되지 않아야 함
        assertDoesNotThrow(() -> scheduler.checkAndTimeoutSessions());

        // 두 세션 모두 호출되어야 함 (한 세션의 실패가 다른 세션 처리를 막으면 안 됨)
        verify(sessionService, times(1))
                .markAsFailedIfStillInProgress(eq(10L), any(LocalDateTime.class));
        verify(sessionService, times(1))
                .markAsFailedIfStillInProgress(eq(11L), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("[동시성] markAsFailed가 false를 리턴(이미 COMPLETED)하면 정상 진행해야 함")
    void testYieldWhenAlreadyCompleted() {
        when(sessionRepository.findByStatus(Status.IN_PROGRESS))
                .thenReturn(List.of(testSession));
        // FastAPI가 한 발 빨라 IN_PROGRESS가 아니게 된 경우
        when(sessionService.markAsFailedIfStillInProgress(eq(1L), any(LocalDateTime.class)))
                .thenReturn(false);

        assertDoesNotThrow(() -> scheduler.checkAndTimeoutSessions());

        verify(sessionService, times(1))
                .markAsFailedIfStillInProgress(eq(1L), any(LocalDateTime.class));
    }
}