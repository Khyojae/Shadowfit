package com.shadowfit.controller;

import com.shadowfit.dto.report.record.CalendarMainResponseDto;
import com.shadowfit.dto.report.record.DailyActivityResponseDto;
import com.shadowfit.dto.report.record.DailyLogRequestDto;
import com.shadowfit.dto.report.record.WeeklyActivityResponseDto;
import com.shadowfit.global.security.auth.CustomUserDetails;
import com.shadowfit.service.Exercise.SessionService;
import com.shadowfit.service.Report.DailyLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "운동 활동 관리", description = "메인페이지 운동 활동 관리")
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
@Slf4j
public class ExerciseRecordController {
    private final SessionService sessionService;
    private final DailyLogService dailyLogService;

    @Operation(summary="주간 운동 요약",description = "주간 운동 기록 열람 가능")
    @GetMapping("/weekly-summary")
    public ResponseEntity<WeeklyActivityResponseDto> getWeeklySummary(@AuthenticationPrincipal CustomUserDetails customUserDetails) {
        // 서비스 로직에서 주간 통계 및 오늘 운동 리스트를 계산해서 반환
        Long memberId = customUserDetails.getMember().getId();
        WeeklyActivityResponseDto response = sessionService.getWeeklyActivity(memberId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary="메인화면 달력 데이터 조회",description = "메인화면에 달력 api")
    @GetMapping("/calendar")
    public ResponseEntity<CalendarMainResponseDto> getCalendarRecords(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @RequestParam int year,
            @RequestParam int month) {
        Long memberId = customUserDetails.getMember().getId();
        CalendarMainResponseDto response = sessionService.getCalendarMain(memberId, year, month);
        return ResponseEntity.ok(response);
    }

    @Operation(summary="특정 날짜 운동 목록", description = "달력에서 날짜 클릭 시 그 날의 운동 세션 목록 조회. 빈 날은 sessions=[] 반환")
    @GetMapping("/daily")
    public ResponseEntity<DailyActivityResponseDto> getDailyActivity(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long memberId = customUserDetails.getMember().getId();
        DailyActivityResponseDto response = sessionService.getDailyActivity(memberId, date);
        return ResponseEntity.ok(response);
    }

    @Operation(summary="운동 메모",description = "운동 메모를 적을 수 있다")
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
