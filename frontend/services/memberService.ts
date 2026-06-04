import api from './api';
import type { OnboardingResponse, OnboardingPatchRequest } from '@/types/user';

// 백엔드 MemberController prefix: /member
export const memberService = {
  // 온보딩 정보 조회 (GET /member/onboarding/{email})
  getOnboarding: (email: string) =>
    api.get<OnboardingResponse>(`/member/onboarding/${encodeURIComponent(email)}`),

  // 온보딩 단계 저장 (PATCH /member/onboarding/{email})
  updateOnboarding: (email: string, data: OnboardingPatchRequest) =>
    api.patch<OnboardingResponse>(`/member/onboarding/${encodeURIComponent(email)}`, data),
};
