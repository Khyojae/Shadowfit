package com.shadowfit.dto.exercises.session;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SessionUpdateRequestDto {
    private int totalReps;
    private double avgSyncRate;
    private double maxSyncRate;
    private double minSyncRate;
    private double caloriesBurned;
    private int difficultyLevel;
}
