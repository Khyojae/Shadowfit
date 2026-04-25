package com.shadowfit.dto.login;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "로그인 req dto")
public class LoginRequestDto {
    @Schema(description = "email", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message="이메일은 필수 입력 값입니다.")
    private String email;

    @Schema(description = "PASSWORD",requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message="PASSWORD는 필수 입력 값입니다.")
    private String password;
}
