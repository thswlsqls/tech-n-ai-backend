package com.tech.n.ai.api.auth.service;

import com.tech.n.ai.api.auth.dto.LoginRequest;
import com.tech.n.ai.api.auth.dto.RefreshTokenRequest;
import com.tech.n.ai.api.auth.dto.TokenResponse;
import com.tech.n.ai.common.exception.exception.UnauthorizedException;
import com.tech.n.ai.common.security.jwt.JwtTokenPayload;
import com.tech.n.ai.domain.aurora.entity.auth.RefreshTokenEntity;
import com.tech.n.ai.domain.aurora.entity.auth.UserEntity;
import com.tech.n.ai.domain.aurora.repository.reader.auth.UserReaderRepository;
import com.tech.n.ai.domain.aurora.repository.writer.auth.UserWriterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserAuthenticationService {
    
    private final UserReaderRepository userReaderRepository;
    private final UserWriterRepository userWriterRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    
    @Transactional
    public TokenResponse login(LoginRequest request) {
        UserEntity user = findAndValidateUser(request.email(), request.password());
        user.updateLastLogin();
        userWriterRepository.save(user);
        return tokenService.generateTokens(user.getId(), user.getEmail(), TokenConstants.USER_ROLE);
    }

    @Transactional
    public void logout(String userId, String refreshToken) {
        RefreshTokenEntity tokenEntity = findAndValidateRefreshToken(refreshToken, userId);
        refreshTokenService.deleteRefreshToken(tokenEntity);
    }
    
    @Transactional
    public TokenResponse refreshToken(RefreshTokenRequest request) {
        if (!tokenService.validateToken(request.refreshToken())) {
            throw new UnauthorizedException("유효하지 않은 Refresh Token입니다.");
        }

        RefreshTokenEntity tokenEntity = refreshTokenService.findRefreshToken(request.refreshToken())
            .orElseThrow(() -> new UnauthorizedException("유효하지 않은 Refresh Token입니다."));

        validateTokenExpiry(tokenEntity);

        JwtTokenPayload payload = tokenService.getPayloadFromToken(request.refreshToken());

        if ("ADMIN".equals(payload.role())) {
            throw new UnauthorizedException("관리자 토큰은 관리자 전용 갱신 경로를 사용하세요.");
        }

        if (tokenEntity.getUserId() == null || !tokenEntity.getUserId().equals(Long.parseLong(payload.userId()))) {
            throw new UnauthorizedException("Refresh Token의 사용자 ID가 일치하지 않습니다.");
        }

        refreshTokenService.deleteRefreshToken(tokenEntity);

        return tokenService.generateTokens(Long.parseLong(payload.userId()), payload.email(), payload.role());
    }
    
    private UserEntity findAndValidateUser(String email, String password) {
        UserEntity user = userReaderRepository.findByEmail(email)
            .orElseThrow(() -> new UnauthorizedException("이메일 또는 비밀번호가 올바르지 않습니다."));
        
        if (!user.isActive()) {
            throw new UnauthorizedException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        
        if (user.getPassword() == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new UnauthorizedException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        
        return user;
    }
    
    private RefreshTokenEntity findAndValidateRefreshToken(String refreshToken, String userId) {
        RefreshTokenEntity entity = refreshTokenService.findRefreshToken(refreshToken)
            .orElseThrow(() -> new UnauthorizedException("유효하지 않은 Refresh Token입니다."));
        
        if (!entity.getUserId().toString().equals(userId)) {
            throw new UnauthorizedException("Refresh Token의 사용자 ID가 일치하지 않습니다.");
        }
        
        return entity;
    }
    
    private void validateTokenExpiry(RefreshTokenEntity entity) {
        if (entity.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException("만료된 Refresh Token입니다.");
        }
    }
}
