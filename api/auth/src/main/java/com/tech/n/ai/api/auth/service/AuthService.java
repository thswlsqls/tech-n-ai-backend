package com.tech.n.ai.api.auth.service;

import com.tech.n.ai.api.auth.dto.*;
import com.tech.n.ai.common.exception.exception.ConflictException;
import com.tech.n.ai.common.exception.exception.ResourceNotFoundException;
import com.tech.n.ai.common.exception.exception.UnauthorizedException;
import com.tech.n.ai.domain.aurora.entity.auth.RefreshTokenEntity;
import com.tech.n.ai.domain.aurora.entity.auth.UserEntity;
import com.tech.n.ai.domain.aurora.repository.reader.auth.RefreshTokenReaderRepository;
import com.tech.n.ai.domain.aurora.repository.reader.auth.UserReaderRepository;
import com.tech.n.ai.domain.aurora.repository.writer.auth.UserWriterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    
    private final UserReaderRepository userReaderRepository;
    private final UserWriterRepository userWriterRepository;
    private final RefreshTokenReaderRepository refreshTokenReaderRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final UserValidator userValidator;
    private final EmailVerificationService emailVerificationService;
    private final UserAuthenticationService userAuthenticationService;
    private final OAuthService oauthService;
    
    @Transactional
    public AuthResponse signup(SignupRequest request) {
        userValidator.validateEmailNotExists(request.email());
        userValidator.validateUsernameNotExists(request.username());
        
        UserEntity user = UserEntity.createNewUser(
            request.email(),
            request.username(),
            passwordEncoder.encode(request.password())
        );
        userWriterRepository.save(user);
        emailVerificationService.createEmailVerificationToken(request.email());
        
        return new AuthResponse(
            user.getId(),
            user.getEmail(),
            user.getUsername(),
            "회원가입이 완료되었습니다. 이메일 인증을 완료해주세요."
        );
    }
    
    public TokenResponse login(LoginRequest request) {
        return userAuthenticationService.login(request);
    }
    
    public void logout(String userId, String refreshToken) {
        userAuthenticationService.logout(userId, refreshToken);
    }
    
    public TokenResponse refreshToken(RefreshTokenRequest request) {
        return userAuthenticationService.refreshToken(request);
    }
    
    public void verifyEmail(String token) {
        emailVerificationService.verifyEmail(token);
    }
    
    public void requestPasswordReset(ResetPasswordRequest request) {
        emailVerificationService.requestPasswordReset(request);
    }
    
    public void confirmPasswordReset(ResetPasswordConfirmRequest request) {
        emailVerificationService.confirmPasswordReset(request);
    }
    
    public String startOAuthLogin(String providerName) {
        return oauthService.startOAuthLogin(providerName);
    }
    
    public TokenResponse handleOAuthCallback(String providerName, String code, String state) {
        return oauthService.handleOAuthCallback(providerName, code, state);
    }
    
    /**
     * 회원탈퇴
     * 
     * @param userId 사용자 ID
     * @param request 탈퇴 요청
     */
    @Transactional
    public void withdraw(String userId, WithdrawRequest request) {
        // 1. 사용자 조회 및 검증
        UserEntity user = findAndValidateUser(userId);
        
        // 2. 비밀번호 확인 (선택적)
        validatePasswordIfProvided(user, request);
        
        // 3. 모든 RefreshToken 삭제
        deleteAllRefreshTokens(user.getId());
        
        // 4. Unique 컬럼 익명화 (재가입 시 unique constraint 충돌 방지)
        String suffix = "deleted_" + System.currentTimeMillis() + "_";
        user.setEmail(suffix + user.getEmail());
        user.setUsername(suffix + user.getUsername());

        // 5. User 엔티티 Soft Delete
        user.setDeletedBy(user.getId());
        userWriterRepository.delete(user);
        
        log.info("User withdrawal completed: userId={}, email={}", user.getId(), user.getEmail());
    }
    
    /**
     * 사용자 조회 및 검증
     */
    private UserEntity findAndValidateUser(String userId) {
        UserEntity user = userReaderRepository.findById(Long.parseLong(userId))
            .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
        
        if (!user.isActive()) {
            throw new ConflictException("이미 탈퇴된 사용자입니다.");
        }
        
        return user;
    }
    
    /**
     * 비밀번호 확인 (선택적)
     */
    private void validatePasswordIfProvided(UserEntity user, WithdrawRequest request) {
        if (request == null || request.password() == null || request.password().isBlank()) {
            return; // 비밀번호 확인 생략
        }
        
        if (user.getPassword() == null) {
            throw new UnauthorizedException("OAuth 사용자는 비밀번호 확인이 필요하지 않습니다.");
        }
        
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new UnauthorizedException("비밀번호가 일치하지 않습니다.");
        }
    }
    
    /**
     * 모든 RefreshToken 삭제
     */
    private void deleteAllRefreshTokens(Long userId) {
        List<RefreshTokenEntity> refreshTokens = refreshTokenReaderRepository.findByUserId(userId);
        
        for (RefreshTokenEntity token : refreshTokens) {
            if (!Boolean.TRUE.equals(token.getIsDeleted())) {
                refreshTokenService.deleteRefreshToken(token);
            }
        }
        
        log.debug("Deleted {} refresh tokens for user: userId={}", refreshTokens.size(), userId);
    }
}
