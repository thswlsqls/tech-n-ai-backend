package com.tech.n.ai.api.auth.service;

import com.tech.n.ai.api.auth.config.OAuthProperties;
import com.tech.n.ai.api.auth.dto.OAuthUserInfo;
import com.tech.n.ai.api.auth.dto.TokenResponse;
import com.tech.n.ai.api.auth.oauth.OAuthProvider;
import com.tech.n.ai.api.auth.oauth.OAuthProviderFactory;
import com.tech.n.ai.api.auth.oauth.OAuthStateService;
import com.tech.n.ai.common.exception.exception.ResourceNotFoundException;
import com.tech.n.ai.common.exception.exception.UnauthorizedException;
import com.tech.n.ai.domain.aurora.entity.auth.ProviderEntity;
import com.tech.n.ai.domain.aurora.entity.auth.UserEntity;
import com.tech.n.ai.domain.aurora.repository.reader.auth.ProviderReaderRepository;
import com.tech.n.ai.domain.aurora.repository.reader.auth.UserReaderRepository;
import com.tech.n.ai.domain.aurora.repository.writer.auth.UserWriterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OAuthService 단위 테스트")
class OAuthServiceTest {

    @Mock
    private ProviderReaderRepository providerReaderRepository;

    @Mock
    private UserReaderRepository userReaderRepository;

    @Mock
    private UserWriterRepository userWriterRepository;

    @Mock
    private OAuthProviderFactory oauthProviderFactory;

    @Mock
    private OAuthStateService oauthStateService;

    @Mock
    private OAuthProperties oauthProperties;

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private OAuthService oauthService;

    // ========== startOAuthLogin ==========

    @Nested
    @DisplayName("startOAuthLogin")
    class StartOAuthLogin {

        @Test
        @DisplayName("정상 - 인증 URL 반환")
        void startOAuthLogin_성공() {
            // Given
            ProviderEntity provider = createProvider();
            OAuthProvider mockOAuthProvider = mock(OAuthProvider.class);
            OAuthProperties.GoogleOAuthProperties googleProps = new OAuthProperties.GoogleOAuthProperties();
            googleProps.setRedirectUri("http://localhost:8082/callback");

            when(providerReaderRepository.findByName("GOOGLE")).thenReturn(Optional.of(provider));
            when(oauthProviderFactory.getProvider("google")).thenReturn(mockOAuthProvider);
            when(oauthProperties.getGoogle()).thenReturn(googleProps);
            when(mockOAuthProvider.generateAuthorizationUrl(eq("client-id"), eq("http://localhost:8082/callback"), anyString()))
                .thenReturn("https://accounts.google.com/o/oauth2/auth?state=abc");

            // When
            String result = oauthService.startOAuthLogin("google");

            // Then
            assertThat(result).contains("google.com");
            verify(oauthStateService).saveState(anyString(), eq("GOOGLE"));
        }

        @Test
        @DisplayName("지원하지 않는 Provider - ResourceNotFoundException")
        void startOAuthLogin_미지원_Provider() {
            when(providerReaderRepository.findByName("UNKNOWN")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> oauthService.startOAuthLogin("unknown"))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("비활성화된 Provider - UnauthorizedException")
        void startOAuthLogin_비활성화_Provider() {
            ProviderEntity provider = createProvider();
            provider.setIsEnabled(false);
            when(providerReaderRepository.findByName("GOOGLE")).thenReturn(Optional.of(provider));

            assertThatThrownBy(() -> oauthService.startOAuthLogin("google"))
                .isInstanceOf(UnauthorizedException.class);
        }
    }

    // ========== handleOAuthCallback ==========

    @Nested
    @DisplayName("handleOAuthCallback")
    class HandleOAuthCallback {

        @Test
        @DisplayName("신규 사용자 - 계정 생성 후 TokenResponse 반환")
        void handleOAuthCallback_신규_사용자() {
            // Given
            ProviderEntity provider = createProvider();
            OAuthProvider mockOAuthProvider = mock(OAuthProvider.class);
            OAuthProperties.GoogleOAuthProperties googleProps = new OAuthProperties.GoogleOAuthProperties();
            googleProps.setRedirectUri("http://localhost:8082/callback");
            OAuthUserInfo userInfo = OAuthUserInfo.builder()
                .providerUserId("google-user-123")
                .email("user@gmail.com")
                .username("googleuser")
                .build();
            TokenResponse expected = new TokenResponse("access", "refresh", "Bearer", 3600L, 604800L);

            when(providerReaderRepository.findByName("GOOGLE")).thenReturn(Optional.of(provider));
            when(oauthProviderFactory.getProvider("google")).thenReturn(mockOAuthProvider);
            when(oauthProperties.getGoogle()).thenReturn(googleProps);
            when(mockOAuthProvider.exchangeAccessToken(eq("auth-code"), eq("client-id"), eq("client-secret"), eq("http://localhost:8082/callback")))
                .thenReturn("oauth-access-token");
            when(mockOAuthProvider.getUserInfo("oauth-access-token")).thenReturn(userInfo);
            when(userReaderRepository.findByProviderIdAndProviderUserId(1L, "google-user-123"))
                .thenReturn(Optional.empty());
            when(userWriterRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
                UserEntity saved = invocation.getArgument(0);
                saved.setId(1L);
                return saved;
            });
            when(tokenService.generateTokens(1L, "user@gmail.com", "USER")).thenReturn(expected);

            // When
            TokenResponse result = oauthService.handleOAuthCallback("google", "auth-code", "state-value");

            // Then
            assertThat(result).isEqualTo(expected);
            verify(oauthStateService).validateAndDeleteState("state-value", "GOOGLE");
            verify(userWriterRepository).save(any(UserEntity.class));
        }

        @Test
        @DisplayName("기존 사용자 - 정보 업데이트 후 TokenResponse 반환")
        void handleOAuthCallback_기존_사용자() {
            // Given
            ProviderEntity provider = createProvider();
            OAuthProvider mockOAuthProvider = mock(OAuthProvider.class);
            OAuthProperties.GoogleOAuthProperties googleProps = new OAuthProperties.GoogleOAuthProperties();
            googleProps.setRedirectUri("http://localhost:8082/callback");
            OAuthUserInfo userInfo = OAuthUserInfo.builder()
                .providerUserId("google-user-123")
                .email("updated@gmail.com")
                .username("updateduser")
                .build();
            UserEntity existingUser = new UserEntity();
            existingUser.setId(100L);
            existingUser.setEmail("old@gmail.com");
            existingUser.setIsDeleted(false);
            TokenResponse expected = new TokenResponse("access", "refresh", "Bearer", 3600L, 604800L);

            when(providerReaderRepository.findByName("GOOGLE")).thenReturn(Optional.of(provider));
            when(oauthProviderFactory.getProvider("google")).thenReturn(mockOAuthProvider);
            when(oauthProperties.getGoogle()).thenReturn(googleProps);
            when(mockOAuthProvider.exchangeAccessToken(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("oauth-access-token");
            when(mockOAuthProvider.getUserInfo("oauth-access-token")).thenReturn(userInfo);
            when(userReaderRepository.findByProviderIdAndProviderUserId(1L, "google-user-123"))
                .thenReturn(Optional.of(existingUser));
            when(userWriterRepository.save(existingUser)).thenReturn(existingUser);
            when(tokenService.generateTokens(100L, "updated@gmail.com", "USER")).thenReturn(expected);

            // When
            TokenResponse result = oauthService.handleOAuthCallback("google", "auth-code", "state-value");

            // Then
            assertThat(result).isEqualTo(expected);
            assertThat(existingUser.getEmail()).isEqualTo("updated@gmail.com");
        }

        @Test
        @DisplayName("사용자 정보 조회 실패 - UnauthorizedException")
        void handleOAuthCallback_사용자정보_null() {
            // Given
            ProviderEntity provider = createProvider();
            OAuthProvider mockOAuthProvider = mock(OAuthProvider.class);
            OAuthProperties.GoogleOAuthProperties googleProps = new OAuthProperties.GoogleOAuthProperties();
            googleProps.setRedirectUri("http://localhost:8082/callback");

            when(providerReaderRepository.findByName("GOOGLE")).thenReturn(Optional.of(provider));
            when(oauthProviderFactory.getProvider("google")).thenReturn(mockOAuthProvider);
            when(oauthProperties.getGoogle()).thenReturn(googleProps);
            when(mockOAuthProvider.exchangeAccessToken(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("oauth-access-token");
            when(mockOAuthProvider.getUserInfo("oauth-access-token")).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> oauthService.handleOAuthCallback("google", "auth-code", "state-value"))
                .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("삭제된 기존 사용자 - 새 계정 생성")
        void handleOAuthCallback_삭제된_사용자_재가입() {
            // Given
            ProviderEntity provider = createProvider();
            OAuthProvider mockOAuthProvider = mock(OAuthProvider.class);
            OAuthProperties.GoogleOAuthProperties googleProps = new OAuthProperties.GoogleOAuthProperties();
            googleProps.setRedirectUri("http://localhost:8082/callback");
            OAuthUserInfo userInfo = OAuthUserInfo.builder()
                .providerUserId("google-user-123")
                .email("user@gmail.com")
                .username("googleuser")
                .build();
            UserEntity deletedUser = new UserEntity();
            deletedUser.setId(100L);
            deletedUser.setIsDeleted(true);
            TokenResponse expected = new TokenResponse("access", "refresh", "Bearer", 3600L, 604800L);

            when(providerReaderRepository.findByName("GOOGLE")).thenReturn(Optional.of(provider));
            when(oauthProviderFactory.getProvider("google")).thenReturn(mockOAuthProvider);
            when(oauthProperties.getGoogle()).thenReturn(googleProps);
            when(mockOAuthProvider.exchangeAccessToken(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("oauth-access-token");
            when(mockOAuthProvider.getUserInfo("oauth-access-token")).thenReturn(userInfo);
            when(userReaderRepository.findByProviderIdAndProviderUserId(1L, "google-user-123"))
                .thenReturn(Optional.of(deletedUser));
            when(userWriterRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
                UserEntity saved = invocation.getArgument(0);
                saved.setId(200L);
                return saved;
            });
            when(tokenService.generateTokens(200L, "user@gmail.com", "USER")).thenReturn(expected);

            // When
            TokenResponse result = oauthService.handleOAuthCallback("google", "auth-code", "state-value");

            // Then
            assertThat(result).isEqualTo(expected);
        }
    }

    // ========== 헬퍼 ==========

    private ProviderEntity createProvider() {
        ProviderEntity provider = new ProviderEntity();
        provider.setId(1L);
        provider.setName("GOOGLE");
        provider.setDisplayName("Google");
        provider.setClientId("client-id");
        provider.setClientSecret("client-secret");
        provider.setIsEnabled(true);
        return provider;
    }
}
