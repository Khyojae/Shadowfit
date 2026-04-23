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
import com.shadowfit.repository.ExercisesRepository;
import com.shadowfit.repository.MemberRepository;
import com.shadowfit.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
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

    /**
     * [세션 생성] 새로운 운동 분석 프로세스를 시작하기 위한 초기 레코드를 생성합니다.
     *
     * @param appDto          사용자가 선택한 운동 및 영상 정보
     * @param currentMemberId 현재 로그인한 사용자 ID
     * @return 생성된 세션 엔티티
     */
    @Transactional
    public Session createSession(VideoRequestDto appDto, Long currentMemberId) {
        Member member = memberRepository.findById(currentMemberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Exercise exercise = exercisesRepository.findById(appDto.getExerciseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.EXERCISE_NOT_FOUND));

        Session session = Session.builder()
                .user(member)
                .exercise(exercise)
                .referenceSource(appDto.getReferenceSource())
                .startTime(LocalDateTime.now())
                .status(Status.IN_PROGRESS)
                .build();

        return sessionRepository.save(session);
    }

    /**
     * [세션 완료] AI 서버로부터 수신한 분석 결과를 바탕으로 세션을 최종 업데이트합니다.
     *
     * @param request AI 서버(gRPC)에서 넘어온 최종 분석 데이터
     */
    @Transactional
    public void completeSession(SessionCompleteRequest request) {
        Session session = sessionRepository.findById(request.getSessionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));

        session.setStatus(Status.COMPLETED);
        session.setEndTime(LocalDateTime.now());

        session.setTotalReps(request.getTotalReps());
        session.setAvgSyncRate(java.math.BigDecimal.valueOf(request.getAvgSyncRate()));
        session.setCaloriesBurned(java.math.BigDecimal.valueOf(request.getCaloriesBurned()));

        sessionRepository.save(session);
    }

    /**
     * ✅ 구버전 WebClient 방식 (필요 시 사용)
     */
    @Transactional
    public Long sendToAnalysisServer(VideoRequestDto appDto, Long currentMemberId) {
        Member member = memberRepository.findById(currentMemberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Exercise exercise = exercisesRepository.findById(appDto.getExerciseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.EXERCISE_NOT_FOUND));

        Session session = Session.builder()
                .user(member)
                .exercise(exercise)
                .referenceSource(appDto.getReferenceSource())
                .startTime(LocalDateTime.now())
                .status(Status.IN_PROGRESS)
                .build();

        Session savedSession = sessionRepository.save(session);
        return savedSession.getId();
    }

    @Transactional(readOnly = true)
    public WeeklyActivityResponseDto getWeeklyActivity(Long memberId) {

        // 1. 이번 주 시작일(월)과 종료일(일) 계산
        LocalDate today = LocalDate.now();
        LocalDate startOfWeek = today.with(java.time.DayOfWeek.MONDAY);
        LocalDate endOfWeek = today.with(java.time.DayOfWeek.SUNDAY);

        // 2. 이번 주 모든 세션 조회
        List<Session> weeklySessions = sessionRepository.findByUserIdAndStartTimeBetween(
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

        List<Session> monthlySessions = sessionRepository.findByUserIdAndStartTimeBetween(
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
            boolean hasRecord = sessionRepository.findByUserIdAndStartTimeBetween(
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