package com.shadowfit.controller;

import com.shadowfit.dto.report.record.CalendarMainResponseDto;
import com.shadowfit.dto.report.record.DailyLogRequestDto;
import com.shadowfit.dto.report.record.WeeklyActivityResponseDto;
import com.shadowfit.global.security.auth.CustomUserDetails;
import com.shadowfit.service.Exercise.SessionService;
import com.shadowfit.service.Report.DailyLogServcie;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "운동 활동 관리", description = "메인페이지 운동 활동 관리")
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
@Slf4j
public class ExerciseRecordController {
    private final SessionService sessionService;
    private final DailyLogServcie dailyLogService;

    @GetMapping("/weekly-summary")
    public ResponseEntity<WeeklyActivityResponseDto> getWeeklySummary(@AuthenticationPrincipal CustomUserDetails customUserDetails) {
        // 서비스 로직에서 주간 통계 및 오늘 운동 리스트를 계산해서 반환
        Long memberId = customUserDetails.getMember().getId();
        WeeklyActivityResponseDto response = sessionService.getWeeklyActivity(memberId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/calendar")
    public ResponseEntity<CalendarMainResponseDto> getCalendarRecords(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @RequestParam int year,
            @RequestParam int month) {
        Long memberId = customUserDetails.getMember().getId();
        CalendarMainResponseDto response = sessionService.getCalendarMain(memberId, year, month);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/daily-logs")
    public ResponseEntity<Void> saveDailyLog(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @RequestBody DailyLogRequestDto request) {
        if (customUserDetails == null) {
            log.error("#### [ERROR] 인증 객체가 비어있습니다!");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Long memberId = customUserDetails.getMember().getId();
        log.info("#### [DEBUG] 인증 성공! memberId: {}", memberId);

        dailyLogService.saveOrUpdateLog(memberId, request);
        return ResponseEntity.noContent().build();
    }




}
