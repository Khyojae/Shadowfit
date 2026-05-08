package com.shadowfit.service.Exercise;

import com.shadowfit.model.exercise.Session;
import com.shadowfit.model.exercise.Status;
import com.shadowfit.repository.exercise.SessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 운동 세션 타임아웃 관리 스케줄러
 *
 * 네트워크 장애로 FastAPI로부터 분석 결과를 받지 못한 세션을 자동으로 실패 처리합니다.
 * 예상 운동시간 + 버퍼 시간 이상 IN_PROGRESS 상태가 유지되면 FAILED로 변경합니다.
 *
 * [동시성 정책]
 * 타임아웃 직전에 FastAPI가 완료 결과를 보내올 수 있으므로 Session 엔티티에 @Version 으로
 * 낙관적 락을 걸어두었고, 충돌 발생 시 본 스케줄러는 양보합니다 (FastAPI 결과 우선).
 */
@Slf4j
@Service
public class SessionTimeoutScheduler {

    private final SessionRepository sessionRepository;
    private final SessionService sessionService;
    private final Integer defaultBufferMinutes;

    public SessionTimeoutScheduler(SessionRepository sessionRepository,
                                   SessionService sessionService,
                                   @Value("${exercise.session.timeout.default-buffer-minutes:30}") Integer bufferMinutes) {
        this.sessionRepository = sessionRepository;
        this.sessionService = sessionService;
        this.defaultBufferMinutes = bufferMinutes;
    }

    /**
     * 매 1분마다 타임아웃된 세션을 확인하고 FAILED 상태로 변경합니다.
     *
     * 타임아웃 계산식:
     * 타임아웃시간 = 세션 시작시간 + (예상 운동시간 + 버퍼시간)
     */
    @Scheduled(fixedDelayString = "${exercise.session.timeout.check-interval-minutes:1}m",
               initialDelayString = "30s")
    public void checkAndTimeoutSessions() {
        try {
            log.debug("세션 타임아웃 체크 시작 - 버퍼시간: {}분", defaultBufferMinutes);

            List<Session> inProgressSessions = sessionRepository.findByStatus(Status.IN_PROGRESS);

            if (inProgressSessions.isEmpty()) {
                log.debug("타임아웃 체크 대상 세션 없음");
                return;
            }

            LocalDateTime now = LocalDateTime.now();
            int timeoutCount = 0;
            int yieldedCount = 0;

            for (Session session : inProgressSessions) {
                LocalDateTime timeoutThreshold = session.getStartTime()
                        .plusMinutes(session.getExercise().getExpectedDurationMinutes())
                        .plusMinutes(defaultBufferMinutes);

                if (!now.isAfter(timeoutThreshold)) {
                    continue;
                }

                try {
                    boolean changed = sessionService.markAsFailedIfStillInProgress(session.getId(), now);
                    if (changed) {
                        log.warn("세션 타임아웃 처리 - 세션 ID: {}, 멤버: {}, 운동: {}, 시작시간: {}, 타임아웃기준: {}",
                                session.getId(),
                                session.getMember().getId(),
                                session.getExercise().getName(),
                                session.getStartTime(),
                                timeoutThreshold);
                        timeoutCount++;
                    }
                } catch (ObjectOptimisticLockingFailureException e) {
                    // FastAPI 완료 콜백이 동시에 같은 세션을 갱신함. 결과 데이터가 더 가치있으므로 양보.
                    yieldedCount++;
                    log.info("세션 타임아웃 양보 - 세션 ID: {} (FastAPI 결과 우선)", session.getId());
                } catch (Exception e) {
                    log.error("세션 {} 타임아웃 처리 실패", session.getId(), e);
                }
            }

            if (timeoutCount > 0 || yieldedCount > 0) {
                log.info("세션 타임아웃 처리 완료 - FAILED 전환: {}건, FastAPI 양보: {}건",
                        timeoutCount, yieldedCount);
            }

        } catch (Exception e) {
            log.error("세션 타임아웃 체크 중 에러 발생", e);
        }
    }
}