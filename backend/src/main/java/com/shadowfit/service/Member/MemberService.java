package com.shadowfit.service.Member;

import com.shadowfit.dto.login.*;
import com.shadowfit.global.error.BusinessException;
import com.shadowfit.global.error.ErrorCode;
import com.shadowfit.global.security.jwt.JwtBlacklist;
import com.shadowfit.global.security.jwt.JwtUtil;
import com.shadowfit.model.member.Member;
import com.shadowfit.model.member.RefreshToken;
import com.shadowfit.model.member.UserRole;
import com.shadowfit.repository.MemberRepository;
import com.shadowfit.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService{
    private final JwtUtil jwtUtil;
    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtBlacklist jwtBlacklist;
    //로그인 로직
    @Transactional
    public LoginResponseDto login(LoginRequestDto dto){
        Member member = memberRepository.findByEmail(dto.getEmail()).
                orElseThrow(()-> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if(!passwordEncoder.matches(dto.getPassword(), member.getPassword())){
            throw new BusinessException(ErrorCode.LOGIN_INPUT_INVALID);
        }

        CustomUserInfoDto info = CustomUserInfoDto.builder()
                .email(member.getEmail())
                .role(member.getRole())
                .build();

        String accessToken=jwtUtil.createAccessToken(info);
        String refreshToken=jwtUtil.createRefreshToken(info);
        UserRole role = member.getRole();

        RefreshToken refreshTokenEntity= RefreshToken.builder()
                .memberId(member.getId())
                .token(refreshToken)
                .build();
        refreshTokenRepository.save(refreshTokenEntity);
        return new LoginResponseDto(accessToken,refreshToken,role);
    }

    //로그아웃 로직
    @Transactional
    public void logout(LogOutRequestDto dto){
        refreshTokenRepository.deleteByToken(dto.getRefreshToken());
        String token = dto.getAccessToken();
        if(token.startsWith("Bearer ")){
            token = token.substring(7);
        }
        long expiration = jwtUtil.getExpiration(token);
        jwtBlacklist.add(token,expiration);
    }

    //회원가입 로직
    @Transactional
    public String signup(MemberRequestDto dto) {
        if(memberRepository.existsByEmail((dto.getEmail()))) {
            throw new BusinessException(ErrorCode.USERID_DUPLICATION);
        }
        String encodedPassword = passwordEncoder.encode(dto.getPassword());
        Member member = Member.builder()
                .username(dto.getUsername())
                .email(dto.getEmail())
                .password(encodedPassword)
                .role(dto.getRole())
                .build();
        memberRepository.save(member);
        return member.getUsername();
    }

    //회원탈퇴 로직
    @Transactional
    public void deleteAccount(String email){
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(()-> new BusinessException(ErrorCode.USER_NOT_FOUND));
        memberRepository.delete(member);
        refreshTokenRepository.deleteByMemberId(member.getId());
    }
}
