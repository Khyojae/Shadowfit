import api from './api';
import type {
  LoginRequest,
  LoginResponse,
  SignupRequest,
  LogoutRequest,
} from '@/types/auth';

// 백엔드 MemberController prefix: /member
export const authService = {
  login: (data: LoginRequest) =>
    api.post<LoginResponse>('/member/login', data),

  // 백엔드 signup 응답은 단순 String("회원가입 성공" 등)
  signup: (data: SignupRequest) =>
    api.post<string>('/member/signup', data),

  // 백엔드 LogOutRequestDto 가 무엇을 요구하는지 정확히 모르면 토큰 기반으로 보냄
  logout: (data: LogoutRequest) =>
    api.post<void>('/member/logout', data),

  // 회원 탈퇴
  deleteAccount: (email: string) =>
    api.delete<void>(`/member/${encodeURIComponent(email)}`),
};
