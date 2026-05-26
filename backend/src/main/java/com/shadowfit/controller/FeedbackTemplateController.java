package com.shadowfit.controller;

import com.shadowfit.dto.exercises.feedback.FeedbackTemplateDto;
import com.shadowfit.global.security.auth.CustomUserDetails;
import com.shadowfit.model.member.SelectedPersona;
import com.shadowfit.service.Exercise.FeedbackTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "운동 피드백 템플릿", description = "운동별 자세 피드백 멘트 조회 (페르소나 자동 분기)")
@RestController
@RequestMapping("/exercises/{exerciseId}/feedback-templates")
@RequiredArgsConstructor
public class FeedbackTemplateController {
    private final FeedbackTemplateService templateService;

    @Operation(summary = "운동의 피드백 템플릿 전체 조회",
               description = "세션 시작 시 클라이언트가 호출. 인증 사용자의 selectedPersona 에 매칭되는 멘트, 없으면 NULL fallback.")
    @GetMapping
    public ResponseEntity<List<FeedbackTemplateDto>> getTemplates(
            @PathVariable Long exerciseId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        SelectedPersona persona = userDetails != null ? userDetails.getMember().getSelectedPersona() : null;
        return ResponseEntity.ok(templateService.getTemplatesByExercise(exerciseId, persona));
    }
}