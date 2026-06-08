package com.tech.n.ai.api.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "oauth")
public class OAuthProperties {

    private GoogleOAuthProperties google = new GoogleOAuthProperties();
    private NaverOAuthProperties naver = new NaverOAuthProperties();
    private KakaoOAuthProperties kakao = new KakaoOAuthProperties();

    /**
     * 제공자별 OAuth 설정이 공통으로 제공하는 값.
     * 공통 Provider 골격(AbstractOAuthProvider)이 제공자 종류와 무관하게 접근하기 위한 인터페이스다.
     */
    public interface OAuthProviderProperties {
        String getAuthorizationEndpoint();
        String getRedirectUri();
    }

    @Getter
    @Setter
    public static class GoogleOAuthProperties implements OAuthProviderProperties {
        private String authorizationEndpoint;
        private String redirectUri;
        private String scope = "openid email profile";
    }

    @Getter
    @Setter
    public static class NaverOAuthProperties implements OAuthProviderProperties {
        private String authorizationEndpoint;
        private String redirectUri;
    }

    @Getter
    @Setter
    public static class KakaoOAuthProperties implements OAuthProviderProperties {
        private String authorizationEndpoint;
        private String redirectUri;
    }
}
