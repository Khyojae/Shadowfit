package com.shadowfit.controller;

import com.shadowfit.dto.exercises.feedback.SessionFeedbackEventDto;
import com.shadowfit.dto.exercises.feedback.SessionFeedbackSummaryDto;
import com.shadowfit.global.security.auth.CustomUserDetails;
import com.shadowfit.service.Exercise.SessionFeedbackQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "세션 피드백 조회", description = "리포트 화면용 — 세션의 결함 이벤트 리스트 / 집계")
@RestController
@RequestMapping("/sessions/{sessionId}")
@RequiredArgsConstructor
public class SessionFeedbackController {
    private final SessionFeedbackQueryService queryService;

    @Operation(summary = "세션의 피드백 이벤트 리스트",
               description = "발생 시각 오름차순. 본인 세션만. (MVP — 페이징 없음, 협의 #18)")
    @GetMapping("/feedbacks")
    public ResponseEntity<List<SessionFeedbackEventDto>> getFeedbacks(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(queryService.getEvents(sessionId, userDetails.getMember().getId()));
    }

    @Operation(summary = "세션의 피드백 집계 (type 별 카운트 + sync_rate 통계)",
               description = "리포트 차트용 — feedback_type 별 카운트 + avg/min/max sync_rate (협의 #17)")
    @GetMapping("/feedback-summary")
    public ResponseEntity<SessionFeedbackSummaryDto> getFeedbackSummary(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(queryService.getSummary(sessionId, userDetails.getMember().getId()));
    }
}
