package com.shadowfit.dto.login;

import com.shadowfit.model.member.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "토큰 생성 및 인증 정보 전달용 DTO")
//로그인 시 활용
public class CustomUserInfoDto{
    @Schema(description = "사용자 이메일", example = "user@example.com")
    private String email;

    @Schema(description = "사용자 권한", example = "USER")
    private UserRole role;
}
