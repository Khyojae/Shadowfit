package com.shadowfit.service.Exercise;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shadowfit.dto.exercises.VideoRequestDto;
import com.shadowfit.dto.report.record.CalendarDayDto;
import com.shadowfit.dto.report.record.CalendarMainResponseDto;
import com.shadowfit.dto.report.record.DailyLogSummaryDto;
import com.shadowfit.dto.report.record.WeeklyActivityResponseDto;
import com.shadowfit.global.error.BusinessException;
import com.shadowfit.global.error.ErrorCode;
import com.shadowfit.model.exercise.Exercise;
import com.shadowfit.model.exercise.Session;
import com.shadowfit.model.exercise.Status;
import com.shadowfit.model.member.Member;
import com.shadowfit.repository.exercise.ExercisesRepository;
import com.shadowfit.repository.member.MemberRepository;
import com.shadowfit.repository.exercise.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.shadowfit.grpc.SessionStatus;
import com.shadowfit.grpc.SessionCompleteRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


//공통세션
@Service
@RequiredArgsConstructor
public class SessionService {
    private final SessionRepository sessionRepository;
    private final ExercisesRepository exercisesRepository;
    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper;

    // 자기 주입: completeSession → applyComplete 호출이 Spring 프록시를 통과해 @Transactional이 적용되도록 함.
    @Lazy
    @Autowired
    private SessionService self;

    // endSession afterCommit 에서 AI 통보용 — 순환 의존 회피 위해 @Lazy
    @Lazy
    @Autowired
    private ExerciseAnalysisService analysisService;

    /**
     * [세션 생성] 새로운 운동 분석 프로세스를 시작하기 위한 초기 레코드를 생성합니다.
     *
     * @param appDto          사용자가 선택한 운동 및 영상 정보
     * @param currentMemberId 현재 로그인한 사용자 ID
     * @return 생성된 세션 엔티티
     */
    @Transactional
    public Session createSession(VideoRequestDto appDto, Long currentMemberId, String finalUrl) {
        Member member = memberRepository.findById(currentMemberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Exercise exercise = exercisesRepository.findById(appDto.getExerciseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.EXERCISE_NOT_FOUND));

        Session session = Session.builder()
                .member(member)
                .exercise(exercise)
                .referenceSource(finalUrl)
                .startTime(LocalDateTime.now())
                .status(Status.IN_PROGRESS)
                .build();

        return sessionRepository.save(session);
    }

    /**
     * [세션 완료] AI 서버로부터 수신한 분석 결과를 바탕으로 세션을 최종 업데이트합니다.
     *
     * 낙관적 락 충돌 시(스케줄러가 동시에 FAILED로 변경한 경우) 재조회하여 COMPLETED로 덮어씁니다.
     * 사용자가 실제로 운동한 데이터(rep/sync rate)는 어떤 경우에도 유실되면 안 됩니다.
     *
     * @param request AI 서버(gRPC)에서 넘어온 최종 분석 데이터
     */
    public void completeSession(SessionCompleteRequest request) {
        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                self.applyComplete(request);
                return;
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
                if (attempt == maxAttempts) {
                    throw e;
                }
                // 동시에 스케줄러가 FAILED로 변경한 케이스. 재조회 후 COMPLETED로 덮어쓰기 위해 재시도.
            }
        }
    }

    @Transactional
    public void applyComplete(SessionCompleteRequest request) {
        Session session = sessionRepository.findById(request.getSessionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));

        // 멱등성: FastAPI가 응답 유실로 같은 결과를 재전송한 경우(2-1, 2-2) 첫 완료 시각/기록을 보존하고 즉시 종료
        if (session.getStatus() == Status.COMPLETED) {
            return;
        }

        session.setStatus(Status.COMPLETED);
        session.setEndTime(LocalDateTime.now());

        session.setTotalReps(request.getTotalReps());
        session.setAvgSyncRate(java.math.BigDecimal.valueOf(request.getAvgSyncRate()));
        session.setCaloriesBurned(java.math.BigDecimal.valueOf(request.getCaloriesBurned()));

        sessionRepository.saveAndFlush(session);
    }

    /**
     * [세션 종료 — 분기 2.A.ET ET-H, 단일 endpoint 분배자 패턴]
     * 클라가 "운동 종료" 버튼 → endTime 기록 + (afterCommit) AI 에 gRPC StopAnalysis 송신.
     *
     * - endTime 만 즉시 기록. 통계 갱신(totalReps/avgSync) 은 AI 의 CompleteAnalysis 콜백이 별도 처리
     * - AI gRPC 호출은 transaction commit 이후 fire-and-forget — 외부 호출이 transaction 안에 끼지 않도록
     * - 본인 세션이 아니면 ACCESS_DENIED, 이미 종료된 세션이면 멱등 (변경 없음, 200 OK)
     * - gRPC 호출 실패 시: SessionTimeoutScheduler 가 safety net (IN_PROGRESS → FAILED)
     */
    @Transactional
    public void endSession(Long sessionId, Long currentMemberId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));

        if (!session.getMember().getId().equals(currentMemberId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        // 멱등: 이미 endTime 기록된 세션은 변경 없음 (AI 재호출도 안 함)
        if (session.getEndTime() != null) {
            return;
        }

        session.setEndTime(LocalDateTime.now());
        sessionRepository.saveAndFlush(session);

        // afterCommit 으로 AI 통보 — DB 변경 확정 후 외부 호출
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        analysisService.stopAnalysis(sessionId);
                    }
                }
        );
    }

    /**
     * [타임아웃 처리] 세션이 아직 IN_PROGRESS 상태이면 FAILED로 변경합니다.
     *
     * 스케줄러 호출용. 별도 트랜잭션으로 실행되어 한 세션의 충돌이 다른 세션 처리에 영향을 주지 않습니다.
     * FastAPI 완료 콜백과 동시 진행 시 OptimisticLockingFailure가 발생할 수 있으며,
     * 이때는 호출 측이 catch하고 양보합니다(FastAPI 결과 우선).
     *
     * @return FAILED로 전환되었으면 true, 이미 다른 상태이거나 세션이 없으면 false
     */
    @Transactional
    public boolean markAsFailedIfStillInProgress(Long sessionId, LocalDateTime endTime) {
        Session session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null || session.getStatus() != Status.IN_PROGRESS) {
            return false;
        }
        session.setStatus(Status.FAILED);
        session.setEndTime(endTime);
        sessionRepository.saveAndFlush(session);
        return true;
    }

    @Transactional(readOnly = true)
    public WeeklyActivityResponseDto getWeeklyActivity(Long memberId) {

        // 1. 이번 주 시작일(월)과 종료일(일) 계산
        LocalDate today = LocalDate.now();
        LocalDate startOfWeek = today.with(java.time.DayOfWeek.MONDAY);
        LocalDate endOfWeek = today.with(java.time.DayOfWeek.SUNDAY);

        // 2. 이번 주 모든 세션 조회
        List<Session> weeklySessions = sessionRepository.findByMemberIdAndStartTimeBetween(
                memberId, startOfWeek.atStartOfDay(), endOfWeek.atTime(23, 59, 59));

        // 3. 통계 계산 (Duration 계산 시 NPE 방어)
        int totalMinutes = weeklySessions.stream()
                .mapToInt(s -> {
                    if (s.getStartTime() == null || s.getEndTime() == null) return 0;
                    return (int) java.time.Duration.between(s.getStartTime(), s.getEndTime()).toMinutes();
                })
                .sum();

        // BigDecimal -> Double 변환 최적화
        double totalCalories = weeklySessions.stream()
                .map(s -> s.getCaloriesBurned() != null ? s.getCaloriesBurned() : java.math.BigDecimal.ZERO)
                .mapToDouble(java.math.BigDecimal::doubleValue)
                .sum();

        // 4. 요일별 그래프 데이터 가공
        List<DailyLogSummaryDto> dailyLogs = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = startOfWeek.plusDays(i);
            int dailyMins = weeklySessions.stream()
                    .filter(s -> s.getStartTime() != null && s.getStartTime().toLocalDate().equals(date))
                    .mapToInt(s -> (int) java.time.Duration.between(s.getStartTime(), s.getEndTime()).toMinutes())
                    .sum();

            dailyLogs.add(new DailyLogSummaryDto(
                    date.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.KOREAN),
                    dailyMins,
                    date.equals(today)
            ));
        }

        List<com.shadowfit.dto.report.detailreport.ExerciseSessionDto> todayDetails = weeklySessions.stream()
                .filter(s -> s.getStartTime() != null && s.getStartTime().toLocalDate().equals(today))
                .map(s -> {
                    com.shadowfit.dto.report.detailreport.ExerciseSessionDto detail = new com.shadowfit.dto.report.detailreport.ExerciseSessionDto();

                    detail.setSessionId(s.getId());
                    detail.setExerciseName(s.getExercise().getName());

                    // 🚩 세트와 횟수를 하나의 문자열로 합쳐서 세팅!
                    // 세트 정보가 DB에 따로 없다면 일단 0세트나 횟수만 표시하게끔 처리합니다.
                    detail.setSetSummary(String.format("0세트 x %d회", s.getTotalReps()));

                    detail.setSyncRate(s.getAvgSyncRate() != null ? s.getAvgSyncRate().doubleValue() : 0.0);

                    return detail;
                })
                .collect(Collectors.toList());

        return WeeklyActivityResponseDto.builder()
                .dateRange(String.format("%d월 %d일 - %d일",
                        startOfWeek.getMonthValue(), startOfWeek.getDayOfMonth(), endOfWeek.getDayOfMonth()))
                .totalWorkouts(weeklySessions.size())
                .totalMinutes(totalMinutes)
                .totalCalories((int) totalCalories)
                .dailyLogs(dailyLogs)
                .todayDetails(todayDetails)
                .build();
    }

    @Transactional(readOnly = true)
    public CalendarMainResponseDto getCalendarMain(Long memberId, int year, int month) {
        // 1. 해당 월의 모든 세션 조회
        LocalDate startOfMonth = LocalDate.of(year, month, 1);
        LocalDate endOfMonth = startOfMonth.with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());

        List<Session> monthlySessions = sessionRepository.findByMemberIdAndStartTimeBetween(
                memberId, startOfMonth.atStartOfDay(), endOfMonth.atTime(23, 59, 59));

        // 2. 상단 카드 데이터 계산 (평균 싱크로율)
        double avgSyncRate = monthlySessions.stream()
                .map(s -> s.getAvgSyncRate()) // 일단 객체로 가져오고
                .mapToDouble(val -> val != null ? val.doubleValue() : 0.0)
                .average()
                .orElse(0.0);

        // 3. 달력 날짜별 기록 여부 표시
        List<CalendarDayDto> dayDtos = monthlySessions.stream()
                .map(s -> s.getStartTime().toLocalDate())
                .distinct()
                .map(date -> {
                    CalendarDayDto dto = new CalendarDayDto();
                    dto.setDate(date.toString());
                    dto.setHasRecord(true);

                    double dailyAvg = monthlySessions.stream()
                            .filter(s -> s.getStartTime().toLocalDate().equals(date))
                            .mapToDouble(s -> s.getAvgSyncRate() != null ? s.getAvgSyncRate().doubleValue() : 0.0)                            .average()
                            .orElse(0.0);

                    dto.setDailyAvgSyncRate(dailyAvg);

                    return dto;
                })
                .collect(Collectors.toList());

        CalendarMainResponseDto response = new CalendarMainResponseDto();
        response.setMonthlyExerciseDays((int) monthlySessions.stream().map(s -> s.getStartTime().toLocalDate()).distinct().count());
        response.setTotalAvgSyncRate((int) avgSyncRate);
        response.setConsecutiveDays(calculateConsecutiveDays(memberId)); // 연속 일수 계산 유틸 호출

        response.setYear(year);   // 파라미터로 받은 year 세팅
        response.setMonth(month); // 파라미터로 받은 month 세팅

        response.setRecords(dayDtos);

        return response;
    }

    private int calculateDuration(Session session) {
        if (session.getStartTime() == null || session.getEndTime() == null) {
            return 0;
        }
        return (int) java.time.Duration.between(session.getStartTime(), session.getEndTime()).toMinutes();
    }

    private int calculateConsecutiveDays(Long memberId) {
        int consecutiveDays = 0;
        LocalDate checkDate = LocalDate.now();

        while (true) {
            // 해당 날짜에 운동 기록이 있는지 확인
            boolean hasRecord = sessionRepository.findByMemberIdAndStartTimeBetween(
                    memberId,
                    checkDate.atStartOfDay(),
                    checkDate.atTime(23, 59, 59)
            ).size() > 0;

            if (hasRecord) {
                consecutiveDays++;
                checkDate = checkDate.minusDays(1); // 하루 전으로 이동해서 계속 체크
            } else {
                // 오늘 기록이 없으면 어제 기록부터 다시 체크 (오늘 아직 안 했을 수도 있으니)
                if (checkDate.equals(LocalDate.now())) {
                    checkDate = checkDate.minusDays(1);
                    continue;
                }
                break; // 기록 끊기면 종료
            }

            // 무한 루프 방지 (최대 100일까지만 체크 등 설정 가능)
            if (consecutiveDays > 100) break;
        }
        return consecutiveDays;
    }
}