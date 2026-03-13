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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserAuthenticationService 단위 테스트")
class UserAuthenticationServiceTest {

    @Mock
    private UserReaderRepository userReaderRepository;

    @Mock
    private UserWriterRepository userWriterRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private UserAuthenticationService userAuthenticationService;

    // ========== login ==========

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("정상 로그인 - TokenResponse 반환")
        void login_성공() {
            // Given
            LoginRequest request = new LoginRequest("test@example.com", "Password1!");
            UserEntity user = createActiveUser();
            TokenResponse expected = new TokenResponse("access", "refresh", "Bearer", 3600L, 604800L);

            when(userReaderRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("Password1!", "$2a$10$encoded")).thenReturn(true);
            when(tokenService.generateTokens(123L, "test@example.com", "USER")).thenReturn(expected);

            // When
            TokenResponse result = userAuthenticationService.login(request);

            // Then
            assertThat(result).isEqualTo(expected);
            verify(userWriterRepository).save(user);
        }

        @Test
        @DisplayName("존재하지 않는 이메일 - UnauthorizedException")
        void login_이메일_미존재() {
            LoginRequest request = new LoginRequest("none@example.com", "Password1!");
            when(userReaderRepository.findByEmail("none@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userAuthenticationService.login(request))
                .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("삭제된 사용자 - UnauthorizedException")
        void login_삭제된_사용자() {
            LoginRequest request = new LoginRequest("test@example.com", "Password1!");
            UserEntity user = createActiveUser();
            user.setIsDeleted(true);
            when(userReaderRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> userAuthenticationService.login(request))
                .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("이메일 미인증 사용자 - UnauthorizedException")
        void login_이메일_미인증() {
            LoginRequest request = new LoginRequest("test@example.com", "Password1!");
            UserEntity user = createActiveUser();
            user.setIsEmailVerified(false);
            when(userReaderRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> userAuthenticationService.login(request))
                .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("비밀번호 불일치 - UnauthorizedException")
        void login_비밀번호_불일치() {
            LoginRequest request = new LoginRequest("test@example.com", "wrong");
            UserEntity user = createActiveUser();
            when(userReaderRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", "$2a$10$encoded")).thenReturn(false);

            assertThatThrownBy(() -> userAuthenticationService.login(request))
                .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("OAuth 사용자(비밀번호 null) - UnauthorizedException")
        void login_OAuth_사용자() {
            LoginRequest request = new LoginRequest("oauth@example.com", "Password1!");
            UserEntity user = createActiveUser();
            user.setPassword(null);
            when(userReaderRepository.findByEmail("oauth@example.com")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> userAuthenticationService.login(request))
                .isInstanceOf(UnauthorizedException.class);
        }
    }

    // ========== logout ==========

    @Nested
    @DisplayName("logout")
    class Logout {

        @Test
        @DisplayName("정상 로그아웃 - RefreshToken 삭제")
        void logout_성공() {
            RefreshTokenEntity tokenEntity = createRefreshToken(123L);
            when(refreshTokenService.findRefreshToken("refresh-token"))
                .thenReturn(Optional.of(tokenEntity));

            userAuthenticationService.logout("123", "refresh-token");

            verify(refreshTokenService).deleteRefreshToken(tokenEntity);
        }

        @Test
        @DisplayName("존재하지 않는 RefreshToken - UnauthorizedException")
        void logout_토큰_미존재() {
            when(refreshTokenService.findRefreshToken("invalid-token"))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> userAuthenticationService.logout("123", "invalid-token"))
                .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("다른 사용자의 RefreshToken - UnauthorizedException")
        void logout_사용자_불일치() {
            RefreshTokenEntity tokenEntity = createRefreshToken(999L);
            when(refreshTokenService.findRefreshToken("refresh-token"))
                .thenReturn(Optional.of(tokenEntity));

            assertThatThrownBy(() -> userAuthenticationService.logout("123", "refresh-token"))
                .isInstanceOf(UnauthorizedException.class);
        }
    }

    // ========== refreshToken ==========

    @Nested
    @DisplayName("refreshToken")
    class RefreshToken {

        @Test
        @DisplayName("정상 토큰 갱신 - 새 TokenResponse 반환")
        void refreshToken_성공() {
            RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh");
            RefreshTokenEntity tokenEntity = createRefreshToken(123L);
            JwtTokenPayload payload = new JwtTokenPayload("123", "test@example.com", "USER");
            TokenResponse expected = new TokenResponse("new-access", "new-refresh", "Bearer", 3600L, 604800L);

            when(tokenService.validateToken("valid-refresh")).thenReturn(true);
            when(refreshTokenService.findRefreshToken("valid-refresh")).thenReturn(Optional.of(tokenEntity));
            when(tokenService.getPayloadFromToken("valid-refresh")).thenReturn(payload);
            when(tokenService.generateTokens(123L, "test@example.com", "USER")).thenReturn(expected);

            TokenResponse result = userAuthenticationService.refreshToken(request);

            assertThat(result).isEqualTo(expected);
            verify(refreshTokenService).deleteRefreshToken(tokenEntity); // 기존 토큰 삭제 확인
        }

        @Test
        @DisplayName("유효하지 않은 JWT - UnauthorizedException")
        void refreshToken_유효하지_않은_JWT() {
            RefreshTokenRequest request = new RefreshTokenRequest("invalid-jwt");
            when(tokenService.validateToken("invalid-jwt")).thenReturn(false);

            assertThatThrownBy(() -> userAuthenticationService.refreshToken(request))
                .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("DB에 없는 RefreshToken - UnauthorizedException")
        void refreshToken_DB_미존재() {
            RefreshTokenRequest request = new RefreshTokenRequest("valid-jwt");
            when(tokenService.validateToken("valid-jwt")).thenReturn(true);
            when(refreshTokenService.findRefreshToken("valid-jwt")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userAuthenticationService.refreshToken(request))
                .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("만료된 RefreshToken - UnauthorizedException")
        void refreshToken_만료된_토큰() {
            RefreshTokenRequest request = new RefreshTokenRequest("expired-refresh");
            RefreshTokenEntity tokenEntity = createRefreshToken(123L);
            tokenEntity.setExpiresAt(LocalDateTime.now().minusDays(1)); // 만료

            when(tokenService.validateToken("expired-refresh")).thenReturn(true);
            when(refreshTokenService.findRefreshToken("expired-refresh")).thenReturn(Optional.of(tokenEntity));

            assertThatThrownBy(() -> userAuthenticationService.refreshToken(request))
                .isInstanceOf(UnauthorizedException.class);
        }
    }

    // ========== 헬퍼 메서드 ==========

    private UserEntity createActiveUser() {
        UserEntity user = new UserEntity();
        user.setId(123L);
        user.setEmail("test@example.com");
        user.setUsername("testuser");
        user.setPassword("$2a$10$encoded");
        user.setIsDeleted(false);
        user.setIsEmailVerified(true);
        return user;
    }

    private RefreshTokenEntity createRefreshToken(Long userId) {
        RefreshTokenEntity token = new RefreshTokenEntity();
        token.setId(1L);
        token.setUserId(userId);
        token.setToken("test-refresh-token");
        token.setExpiresAt(LocalDateTime.now().plusDays(7));
        token.setIsDeleted(false);
        return token;
    }
}
