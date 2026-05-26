package com.shadowfit.service.Report;

import com.shadowfit.dto.report.detailreport.ComparisonWithPreviousDto;
import com.shadowfit.dto.report.detailreport.ExerciseSyncRateDto;
import com.shadowfit.dto.report.detailreport.SessionReportResponseDto;
import com.shadowfit.dto.report.detailreport.WorstSectionDto;
import com.shadowfit.global.error.BusinessException;
import com.shadowfit.global.error.ErrorCode;
import com.shadowfit.model.exercise.PoseData;
import com.shadowfit.model.exercise.Session;
import com.shadowfit.model.exercise.Status;
import com.shadowfit.model.report.Report;
import com.shadowfit.repository.exercise.PoseDataRepository;
import com.shadowfit.repository.report.ReportRepository;
import com.shadowfit.repository.exercise.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {
    private final ReportRepository reportRepository;
    private final SessionRepository sessionRepository;
    private final PoseDataRepository poseDataRepository;

    @Transactional(readOnly = true)
    public SessionReportResponseDto getSessionReport(Long sessionId) {
        log.info("세션 리포트 생성 시작 - 세션 ID: {}", sessionId);

        // 1. 기초 세션 정보 및 운동 조회
        Session currentSession = sessionRepository.findSessionWithExerciseById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));

        // 2. AI 분석 리포트 엔티티 조회
        Report report = reportRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));

        // 3. 이전 동일 운동 세션 조회
        Optional<Session> lastSession = sessionRepository.findFirstByMemberIdAndExerciseIdAndStatusOrderByStartTimeDesc(
                currentSession.getMember().getId(),
                currentSession.getExercise().getId(),
                Status.COMPLETED
        );

        List<PoseData> poseDataList = poseDataRepository.findBySessionIdOrderByTimestampSecAsc(sessionId);
        return buildReportResponse(currentSession, report, lastSession, poseDataList);
    }


    private SessionReportResponseDto buildReportResponse(Session session, Report report,
                                                         Optional<Session> lastSession,
                                                         List<PoseData> poseDataList) {
        SessionReportResponseDto responseDto = SessionReportResponseDto.of(session, report);

        responseDto.setWorstSection(selectWorstSection(session, poseDataList));
        responseDto.setSyncRateDetails(buildSyncRateDetails(session));
        lastSession.ifPresent(last ->
                responseDto.setComparisonWithPrevious(buildComparisonWithPrevious(session, last))
        );

        return responseDto;
    }

    // 연속 WORST_WINDOW_SIZE 개의 PoseData 평균 syncRate 가 가장 낮은 구간을 worst 로 선정.
    // 한 점이 아니라 구간을 보는 이유: 단일 프레임은 노이즈 영향이 커서 일시적 튐을 worst 로 잡을 위험.
    private static final int WORST_WINDOW_SIZE = 3;

    private WorstSectionDto selectWorstSection(Session session, List<PoseData> poseDataList) {
        if (poseDataList == null || poseDataList.size() < WORST_WINDOW_SIZE) {
            return null;
        }

        int worstStart = 0;
        double worstAverage = Double.MAX_VALUE;
        for (int i = 0; i <= poseDataList.size() - WORST_WINDOW_SIZE; i++) {
            double sum = 0.0;
            for (int j = 0; j < WORST_WINDOW_SIZE; j++) {
                Double rate = poseDataList.get(i + j).getSyncRate();
                if (rate == null) {
                    sum = Double.MAX_VALUE;
                    break;
                }
                sum += rate;
            }
            double average = sum / WORST_WINDOW_SIZE;
            if (average < worstAverage) {
                worstAverage = average;
                worstStart = i;
            }
        }

        // 구간의 중앙 PoseData 를 대표 timestamp 로 사용
        PoseData representative = poseDataList.get(worstStart + WORST_WINDOW_SIZE / 2);
        WorstSectionDto worst = new WorstSectionDto();
        worst.setExerciseName(session.getExercise().getName());
        worst.setTimeStamp(formatTimestamp(representative.getTimestampSec()));
        worst.setReason(buildWorstReason(worstAverage, poseDataList, worstStart));
        return worst;
    }

    private String formatTimestamp(Double timestampSec) {
        if (timestampSec == null) return "00:00";
        int totalSeconds = timestampSec.intValue();
        return String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    private String buildWorstReason(double averageSyncRate, List<PoseData> list, int start) {
        int syncPercent = (int) Math.round(averageSyncRate);
        String dominantFeedback = pickDominantFeedback(list, start);
        if (dominantFeedback == null || dominantFeedback.isBlank()) {
            return String.format("싱크로율 %d%%", syncPercent);
        }
        return String.format("싱크로율 %d%% · %s", syncPercent, dominantFeedback);
    }

    // worst 구간 안의 feedback_message 중 가장 자주 등장한 것을 reason 보강에 사용
    private String pickDominantFeedback(List<PoseData> list, int start) {
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (int j = 0; j < WORST_WINDOW_SIZE; j++) {
            String message = list.get(start + j).getFeedbackMessage();
            if (message == null || message.isBlank()) continue;
            counts.merge(message, 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .max(java.util.Map.Entry.comparingByValue())
                .map(java.util.Map.Entry::getKey)
                .orElse(null);
    }

    // 본 프로젝트는 한 세션 = 한 운동 (project-squat-first) 이므로 단일 원소 리스트.
    // BE-09 (세트 도입) 또는 한 세션 다중 운동 정책이 들어오면 이 메서드부터 확장.
    private List<ExerciseSyncRateDto> buildSyncRateDetails(Session session) {
        double avgSyncRate = session.getAvgSyncRate() == null ? 0.0 : session.getAvgSyncRate().doubleValue();
        int totalReps = session.getTotalReps() == null ? 0 : session.getTotalReps();
        ExerciseSyncRateDto detail = new ExerciseSyncRateDto(
                session.getExercise().getId(),
                session.getExercise().getName(),
                String.format("1세트 x %d회", totalReps),
                avgSyncRate
        );
        return List.of(detail);
    }

    private ComparisonWithPreviousDto buildComparisonWithPrevious(Session current, Session last) {
        ComparisonWithPreviousDto dto = new ComparisonWithPreviousDto();
        dto.setSyncRateDiff(diffInt(current.getAvgSyncRate(), last.getAvgSyncRate()));
        dto.setWorkoutMinutesDiff(durationMinutes(current) - durationMinutes(last));
        dto.setCaloriesDiff(diffInt(current.getCaloriesBurned(), last.getCaloriesBurned()));
        return dto;
    }

    private int diffInt(java.math.BigDecimal current, java.math.BigDecimal previous) {
        double currentValue = current == null ? 0.0 : current.doubleValue();
        double previousValue = previous == null ? 0.0 : previous.doubleValue();
        return (int) Math.round(currentValue - previousValue);
    }

    private int durationMinutes(Session session) {
        if (session.getStartTime() == null || session.getEndTime() == null) return 0;
        return (int) java.time.Duration.between(session.getStartTime(), session.getEndTime()).toMinutes();
    }
}