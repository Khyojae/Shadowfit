package com.shadowfit.service.Report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shadowfit.dto.report.detailreport.SessionReportResponseDto;
import com.shadowfit.dto.report.detailreport.WorstSectionDto;
import com.shadowfit.global.error.BusinessException;
import com.shadowfit.global.error.ErrorCode;
import com.shadowfit.model.exercise.PoseData;
import com.shadowfit.model.exercise.Session;
import com.shadowfit.model.exercise.Status;
import com.shadowfit.model.report.Report;
import com.shadowfit.repository.PoseDataRepository;
import com.shadowfit.repository.ReportRepository;
import com.shadowfit.repository.SessionRepository;
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
    private final ObjectMapper objectMapper;

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
        Optional<Session> lastSession = sessionRepository.findFirstByUserIdAndExerciseIdAndStatusOrderByStartTimeDesc(
                currentSession.getUser().getId(),
                currentSession.getExercise().getId(),
                Status.COMPLETED
        );

        List<PoseData> poseDataList = poseDataRepository.findBySessionIdOrderByTimestampSecAsc(sessionId);
        return buildReportResponse(currentSession, report, lastSession, poseDataList);
    }

    private SessionReportResponseDto buildReportResponse(Session session, Report report,
                                                         Optional<Session> lastSession,
                                                         List<PoseData> poseDataList) {
        SessionReportResponseDto responseDto = new SessionReportResponseDto();
        // 1. 기본 수치 매핑
        responseDto.setSessionId(session.getId()); // null 방지
        responseDto.setAvgSyncRate(session.getAvgSyncRate().intValue());
        responseDto.setTotalReps(session.getTotalReps());
        responseDto.setWorkoutMinutes(calculateDuration(session));
        responseDto.setCaloriesBurned(session.getCaloriesBurned().intValue());

        // 2. 리포트 상세 데이터 매핑 (객체 변환 포함)
        responseDto.setAiSafetyReport(report.getImprovementTips());

        if (report.getSummary() != null) {
            WorstSectionDto worst = new WorstSectionDto();

            // 기획서처럼 보이게 하려면 필드를 나눠서 채워야 합니다.
            // 일단 테스트용으로 summary 내용을 exerciseName에 넣어볼게요.
            worst.setExerciseName(report.getSummary());
            worst.setTimeStamp("22:10"); // 실제 데이터가 있다면 파싱해서 넣기
            worst.setReason("싱크로율 저하 및 자세 불균형");

            responseDto.setWorstSection(worst);
        }

        try {
            // 🚩 상세 싱크로율 리스트 매핑 (convertValue 대신 readValue 사용)
            if (report.getDetailedAnalysis() != null) {
                // DB에서 넘어온 데이터가 JSON 형태의 '문자열'이므로 직접 파싱합니다.
                List<com.shadowfit.dto.report.detailreport.ExerciseSyncRateDto> details = objectMapper.readValue(
                        report.getDetailedAnalysis().toString(), // String으로 변환 후 파싱
                        new com.fasterxml.jackson.core.type.TypeReference<List<com.shadowfit.dto.report.detailreport.ExerciseSyncRateDto>>() {}
                );
                responseDto.setSyncRateDetails(details);
            }

            // 🚩 이전 대비 비교 매핑도 동일하게 readValue로 변경
            if (report.getComparisonWithPrevious() != null) {
                com.shadowfit.dto.report.detailreport.ComparisonWithPreviousDto comparison = objectMapper.readValue(
                        report.getComparisonWithPrevious().toString(), // String으로 변환 후 파싱
                        new com.fasterxml.jackson.core.type.TypeReference<com.shadowfit.dto.report.detailreport.ComparisonWithPreviousDto>() {}
                );
                responseDto.setComparisonWithPrevious(comparison);
            }
        } catch (Exception e) {
            log.error("리포트 JSON 데이터 파싱 중 에러 발생: {}", e.getMessage());
            // 에러 발생 시 추적을 위해 e.printStackTrace()를 남겨두는 것도 좋습니다.
            // e.printStackTrace();
        }

        // 3. 이전 세션 비교 로직
        if (lastSession.isPresent()) {
            double diff = session.getAvgSyncRate().doubleValue() - lastSession.get().getAvgSyncRate().doubleValue();
            log.info("이전 대비 싱크로율 변화: {}", diff);
        }

        return responseDto;
    }

    private int calculateDuration(Session session) {
        if (session.getEndTime() == null) return 0;
        return (int) java.time.Duration.between(session.getStartTime(), session.getEndTime()).toMinutes();
    }
}