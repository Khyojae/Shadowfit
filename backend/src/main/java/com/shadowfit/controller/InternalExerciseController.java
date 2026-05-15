package com.shadowfit.controller;

import com.shadowfit.dto.exercises.PoseDataRequestDto;
import com.shadowfit.service.Exercise.PoseDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/internal/exercises")
@RequiredArgsConstructor
public class InternalExerciseController {
    private final PoseDataService poseDataService;

    @Value("${internal.api.token}")
    private String internalToken;

    @PostMapping("/pose-data")
    public ResponseEntity<String> receivePoseData(
            @RequestHeader("X-Internal-Token") String token,
            @RequestBody List<PoseDataRequestDto> dtos
    ) {
        // 내부 통신용 토큰 검증
        if (!internalToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden: Invalid Internal Token");
        }

        poseDataService.savePoseDataBatch(dtos);

        return ResponseEntity.ok("Successfully saved " + dtos.size() + " pose data points.");
    }
}