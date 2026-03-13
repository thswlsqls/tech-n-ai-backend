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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 단위 테스트")
class AuthServiceTest {
    
    @Mock
    private UserReaderRepository userReaderRepository;
    
    @Mock
    private UserWriterRepository userWriterRepository;
    
    @Mock
    private RefreshTokenReaderRepository refreshTokenReaderRepository;
    
    @Mock
    private RefreshTokenService refreshTokenService;
    
    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserValidator userValidator;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private UserAuthenticationService userAuthenticationService;

    @Mock
    private OAuthService oauthService;

    @InjectMocks
    private AuthService authService;

    // ========== signup 테스트 ==========

    @Nested
    @DisplayName("signup")
    class Signup {

        @Test
        @DisplayName("정상 회원가입 - AuthResponse 반환")
        void signup_성공() {
            // Given
            SignupRequest request = new SignupRequest("new@example.com", "newuser", "Password1!");
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$encoded");
            when(userWriterRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
                UserEntity saved = invocation.getArgument(0);
                saved.setId(1L);
                return saved;
            });

            // When
            AuthResponse response = authService.signup(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.email()).isEqualTo("new@example.com");
            assertThat(response.username()).isEqualTo("newuser");
            verify(userValidator).validateEmailNotExists("new@example.com");
            verify(userValidator).validateUsernameNotExists("newuser");
            verify(userWriterRepository).save(any(UserEntity.class));
            verify(emailVerificationService).createEmailVerificationToken("new@example.com");
        }

        @Test
        @DisplayName("이메일 중복 시 ConflictException")
        void signup_이메일_중복() {
            // Given
            SignupRequest request = new SignupRequest("dup@example.com", "newuser", "Password1!");
            doThrow(new ConflictException("email", "이미 사용 중인 이메일입니다."))
                .when(userValidator).validateEmailNotExists("dup@example.com");

            // When & Then
            assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(ConflictException.class);
            verify(userWriterRepository, never()).save(any());
        }

        @Test
        @DisplayName("사용자명 중복 시 ConflictException")
        void signup_사용자명_중복() {
            // Given
            SignupRequest request = new SignupRequest("new@example.com", "dupuser", "Password1!");
            doThrow(new ConflictException("username", "이미 사용 중인 사용자명입니다."))
                .when(userValidator).validateUsernameNotExists("dupuser");

            // When & Then
            assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(ConflictException.class);
            verify(userWriterRepository, never()).save(any());
        }
    }

    // ========== 위임 메서드 테스트 ==========

    @Nested
    @DisplayName("위임 메서드")
    class Delegation {

        @Test
        @DisplayName("login - UserAuthenticationService에 위임")
        void login_위임() {
            LoginRequest request = new LoginRequest("test@example.com", "password");
            TokenResponse expected = new TokenResponse("access", "refresh", "Bearer", 3600L, 604800L);
            when(userAuthenticationService.login(request)).thenReturn(expected);

            TokenResponse result = authService.login(request);

            assertThat(result).isEqualTo(expected);
            verify(userAuthenticationService).login(request);
        }

        @Test
        @DisplayName("logout - UserAuthenticationService에 위임")
        void logout_위임() {
            authService.logout("123", "refresh-token");
            verify(userAuthenticationService).logout("123", "refresh-token");
        }

        @Test
        @DisplayName("refreshToken - UserAuthenticationService에 위임")
        void refreshToken_위임() {
            RefreshTokenRequest request = new RefreshTokenRequest("refresh-token");
            TokenResponse expected = new TokenResponse("new-access", "new-refresh", "Bearer", 3600L, 604800L);
            when(userAuthenticationService.refreshToken(request)).thenReturn(expected);

            TokenResponse result = authService.refreshToken(request);

            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("verifyEmail - EmailVerificationService에 위임")
        void verifyEmail_위임() {
            authService.verifyEmail("token123");
            verify(emailVerificationService).verifyEmail("token123");
        }

        @Test
        @DisplayName("startOAuthLogin - OAuthService에 위임")
        void startOAuthLogin_위임() {
            when(oauthService.startOAuthLogin("google")).thenReturn("https://auth.url");

            String result = authService.startOAuthLogin("google");

            assertThat(result).isEqualTo("https://auth.url");
        }

        @Test
        @DisplayName("handleOAuthCallback - OAuthService에 위임")
        void handleOAuthCallback_위임() {
            TokenResponse expected = new TokenResponse("access", "refresh", "Bearer", 3600L, 604800L);
            when(oauthService.handleOAuthCallback("google", "code", "state")).thenReturn(expected);

            TokenResponse result = authService.handleOAuthCallback("google", "code", "state");

            assertThat(result).isEqualTo(expected);
        }
    }

    // ========== withdraw 테스트 (기존) ==========

    @Test
    @DisplayName("정상적인 회원탈퇴")
    void withdraw_정상_탈퇴() {
        // Given
        String userId = "123";
        UserEntity user = createTestUser();
        WithdrawRequest request = new WithdrawRequest(null, null);
        
        when(userReaderRepository.findById(anyLong())).thenReturn(Optional.of(user));
        when(refreshTokenReaderRepository.findByUserId(anyLong())).thenReturn(Collections.emptyList());
        
        // When
        authService.withdraw(userId, request);
        
        // Then
        verify(userWriterRepository).delete(user);
    }
    
    @Test
    @DisplayName("비밀번호 불일치 시 예외 발생")
    void withdraw_비밀번호_불일치() {
        // Given
        String userId = "123";
        UserEntity user = createTestUser();
        WithdrawRequest request = new WithdrawRequest("wrongPassword", null);
        
        when(userReaderRepository.findById(anyLong())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        
        // When & Then
        assertThrows(UnauthorizedException.class, () -> {
            authService.withdraw(userId, request);
        });
        
        verify(userWriterRepository, never()).delete(any(UserEntity.class));
    }
    
    @Test
    @DisplayName("이메일 미인증 사용자 탈퇴 시도 - ConflictException")
    void withdraw_이메일_미인증_사용자() {
        // Given
        String userId = "123";
        UserEntity user = createTestUser();
        user.setIsEmailVerified(false);
        WithdrawRequest request = new WithdrawRequest(null, null);

        when(userReaderRepository.findById(anyLong())).thenReturn(Optional.of(user));

        // When & Then
        assertThrows(ConflictException.class, () -> {
            authService.withdraw(userId, request);
        });

        verify(userWriterRepository, never()).delete(any(UserEntity.class));
    }

    @Test
    @DisplayName("이미 탈퇴된 사용자 시 예외 발생")
    void withdraw_이미_탈퇴된_사용자() {
        // Given
        String userId = "123";
        UserEntity user = createTestUser();
        user.setIsDeleted(true);
        WithdrawRequest request = new WithdrawRequest(null, null);
        
        when(userReaderRepository.findById(anyLong())).thenReturn(Optional.of(user));
        
        // When & Then
        assertThrows(ConflictException.class, () -> {
            authService.withdraw(userId, request);
        });
        
        verify(userWriterRepository, never()).delete(any(UserEntity.class));
    }
    
    @Test
    @DisplayName("사용자를 찾을 수 없을 때 예외 발생")
    void withdraw_사용자_미존재() {
        // Given
        String userId = "123";
        WithdrawRequest request = new WithdrawRequest(null, null);
        
        when(userReaderRepository.findById(anyLong())).thenReturn(Optional.empty());
        
        // When & Then
        assertThrows(ResourceNotFoundException.class, () -> {
            authService.withdraw(userId, request);
        });
        
        verify(userWriterRepository, never()).delete(any(UserEntity.class));
    }
    
    @Test
    @DisplayName("RefreshToken 삭제 확인")
    void withdraw_RefreshToken_삭제_확인() {
        // Given
        String userId = "123";
        UserEntity user = createTestUser();
        RefreshTokenEntity token1 = createTestRefreshToken();
        RefreshTokenEntity token2 = createTestRefreshToken();
        WithdrawRequest request = new WithdrawRequest(null, null);
        
        when(userReaderRepository.findById(anyLong())).thenReturn(Optional.of(user));
        when(refreshTokenReaderRepository.findByUserId(anyLong()))
            .thenReturn(List.of(token1, token2));
        
        // When
        authService.withdraw(userId, request);
        
        // Then
        verify(refreshTokenService, times(2)).deleteRefreshToken(any(RefreshTokenEntity.class));
        verify(userWriterRepository).delete(user);
    }
    
    @Test
    @DisplayName("OAuth 사용자 비밀번호 확인 시도 시 예외 발생")
    void withdraw_OAuth_사용자_비밀번호_확인_시도() {
        // Given
        String userId = "123";
        UserEntity user = createOAuthUser();
        WithdrawRequest request = new WithdrawRequest("password", null);
        
        when(userReaderRepository.findById(anyLong())).thenReturn(Optional.of(user));
        
        // When & Then
        assertThrows(UnauthorizedException.class, () -> {
            authService.withdraw(userId, request);
        });
        
        verify(userWriterRepository, never()).delete(any(UserEntity.class));
    }
    
    @Test
    @DisplayName("비밀번호 확인 성공")
    void withdraw_비밀번호_확인_성공() {
        // Given
        String userId = "123";
        UserEntity user = createTestUser();
        WithdrawRequest request = new WithdrawRequest("correctPassword", null);
        
        when(userReaderRepository.findById(anyLong())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(refreshTokenReaderRepository.findByUserId(anyLong())).thenReturn(Collections.emptyList());
        
        // When
        authService.withdraw(userId, request);
        
        // Then
        verify(passwordEncoder).matches(eq("correctPassword"), anyString());
        verify(userWriterRepository).delete(user);
    }
    
    private UserEntity createTestUser() {
        UserEntity user = new UserEntity();
        user.setId(123L);
        user.setEmail("test@example.com");
        user.setUsername("testuser");
        user.setPassword("$2a$10$encodedPassword");
        user.setIsDeleted(false);
        user.setIsEmailVerified(true); // 이메일 인증 완료 상태로 설정
        return user;
    }
    
    private UserEntity createOAuthUser() {
        UserEntity user = new UserEntity();
        user.setId(123L);
        user.setEmail("oauth@example.com");
        user.setUsername("oauthuser");
        user.setPassword(null);
        user.setProviderId(1L);
        user.setProviderUserId("oauth123");
        user.setIsDeleted(false);
        user.setIsEmailVerified(true); // OAuth 사용자는 기본적으로 이메일 인증됨
        return user;
    }
    
    private RefreshTokenEntity createTestRefreshToken() {
        RefreshTokenEntity token = new RefreshTokenEntity();
        token.setId(1L);
        token.setUserId(123L);
        token.setToken("test-refresh-token");
        token.setExpiresAt(LocalDateTime.now().plusDays(7));
        token.setIsDeleted(false);
        return token;
    }
}
