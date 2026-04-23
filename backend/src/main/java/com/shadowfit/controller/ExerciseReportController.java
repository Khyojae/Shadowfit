package com.shadowfit.controller;

import com.shadowfit.dto.report.detailreport.SessionReportResponseDto;
import com.shadowfit.global.security.auth.CustomUserDetails;
import com.shadowfit.service.Report.ReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "운동 활동 관리", description = "메인페이지 운동 활동 관리")
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ExerciseReportController {
    private final ReportService reportService;

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<SessionReportResponseDto> getSessionReport(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @PathVariable Long sessionId) {

        SessionReportResponseDto response = reportService.getSessionReport(sessionId);
        return ResponseEntity.ok(response);
    }
}
