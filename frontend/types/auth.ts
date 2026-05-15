// 백엔드 DTO 기준 (com.shadowfit.dto.login, com.shadowfit.model.member.UserRole, Sex)

export type UserRole = 'ADMIN' | 'USER';
export type Sex = 'MALE' | 'FEAMALE' | 'NONE'; // 백엔드 enum 오타 그대로 사용

// 백엔드 LoginRequestDto
export interface LoginRequest {
  email: string;
  password: string;
}

// 백엔드 LoginResponseDto
export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  role: UserRole;
}

// 백엔드 MemberRequestDto
export interface SignupRequest {
  username: string;
  email: string;
  password: string;
  sex: Sex;
  role: UserRole;
}

// 백엔드 LogOutRequestDto - accessToken/refreshToken 둘 다 NotBlank
export interface LogoutRequest {
  accessToken: string;
  refreshToken: string;
}

// 클라이언트에서 보관하는 사용자 식별 정보
// 백엔드에 getMe 엔드포인트가 없어서 토큰에 담긴 정보로만 구성
export interface AuthUser {
  email: string;
  role: UserRole;
}
