package com.shadowfit.dto.exercises.session;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shadowfit.model.exercise.Status;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SessionUpdateResponseDto {
    private Integer sessionId;
    private Status status; // COMPLETED

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime endTime;
}
