package com.shadowfit.service.Member;

import com.shadowfit.dto.onboarding.OnboardingDto;
import com.shadowfit.dto.onboarding.OnboardingRequestDto;
import com.shadowfit.global.error.BusinessException;
import com.shadowfit.global.error.ErrorCode;
import com.shadowfit.model.member.Member;
import com.shadowfit.repository.member.MemberRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@AllArgsConstructor
public class OnboardingService {
    private final MemberRepository memberRepository;

    //온보딩 업데이트
    @Transactional
    public OnboardingDto updateOnboarding(String email, OnboardingRequestDto dto){
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(()->new BusinessException(ErrorCode.USER_NOT_FOUND));
        member.updateOnboarding(dto);

        if (member.getSelectedPersona() != null &&
                member.getWorkoutLevel() != null &&
                member.getHeight() != null &&
                member.getWeight() != null &&
                member.getPreferredurl() != null) {
            member.completeOnboarding();
        }
        return OnboardingDto.fromEntity(member);
    }

    //온보딩 업데이트
    @Transactional(readOnly = true)
    public OnboardingDto readOnboarding(String email){
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(()->new BusinessException(ErrorCode.USER_NOT_FOUND));

        return OnboardingDto.fromEntity(member);
    }

}
