package com.shadowfit.model.member;

import com.shadowfit.dto.onboarding.OnboardingRequestDto;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor(access= AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name="users")
public class Member {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name="selected_persona",nullable=false, length = 10)
    @Builder.Default
    private SelectedPersona selectedPersona = SelectedPersona.Beginner;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Sex sex;

    @CreationTimestamp
    @Column(name="created_at",updatable=false, nullable = false)
    private LocalDateTime createdAt;

    // --- 온보딩 관련 데이터 추가 ---

    @Column(columnDefinition = "DECIMAL(5,1)")
    private Double height;

    @Column(columnDefinition = "DECIMAL(5,1)")
    private Double weight;

    @Enumerated(EnumType.STRING)
    @Column(name = "workout_level")
    private WorkoutLevel workoutLevel;

    @Column(name = "onboarding_completed", nullable = false)
    @Builder.Default
    private boolean onboardingCompleted = false; // 온보딩 완료 여부

    public void completeOnboarding(){
        this.onboardingCompleted = true;
    }

    public void updateOnboarding(OnboardingRequestDto dto){
        if (dto.getSelectedPersona() != null) this.selectedPersona = dto.getSelectedPersona();
        if (dto.getWorkoutLevel() != null) this.workoutLevel = dto.getWorkoutLevel();
        if (dto.getHeight() != null) this.height = dto.getHeight();
        if (dto.getWeight() != null) this.weight = dto.getWeight();
    }

}
