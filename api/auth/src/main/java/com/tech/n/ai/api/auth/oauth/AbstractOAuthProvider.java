package com.tech.n.ai.api.auth.oauth;

import com.tech.n.ai.api.auth.config.OAuthProperties;
import com.tech.n.ai.api.auth.dto.OAuthUserInfo;
import com.tech.n.ai.client.feign.domain.oauth.contract.OAuthDto;
import com.tech.n.ai.client.feign.domain.oauth.contract.OAuthProviderContract;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * OAuth Provider 공통 골격 (Template Method).
 * 토큰 교환과 사용자 정보 조회는 제공자가 같으므로 여기에 두고,
 * 인증 URL 생성만 제공자마다 다른 부분은 기본 구현을 두되 필요한 제공자가 재정의한다.
 */
public abstract class AbstractOAuthProvider implements OAuthProvider {

    private final OAuthProperties.OAuthProviderProperties properties;
    private final OAuthProviderContract oauthApi;

    protected AbstractOAuthProvider(
            OAuthProperties.OAuthProviderProperties properties,
            OAuthProviderContract oauthApi) {
        this.properties = properties;
        this.oauthApi = oauthApi;
    }

    @Override
    public String generateAuthorizationUrl(String clientId, String redirectUri, String state) {
        return UriComponentsBuilder
            .fromUriString(properties.getAuthorizationEndpoint())
            .queryParam("client_id", clientId)
            .queryParam("redirect_uri", resolveRedirectUri(redirectUri))
            .queryParam("response_type", "code")
            .queryParam("state", state)
            .build()
            .toUriString();
    }

    @Override
    public String exchangeAccessToken(String code, String clientId, String clientSecret, String redirectUri) {
        return oauthApi.exchangeAccessToken(
            code,
            clientId,
            clientSecret,
            resolveRedirectUri(redirectUri)
        );
    }

    @Override
    public OAuthUserInfo getUserInfo(String accessToken) {
        OAuthDto.OAuthUserInfo feignUserInfo = oauthApi.getUserInfo(accessToken);

        if (feignUserInfo == null) {
            return null;
        }

        return OAuthUserInfo.builder()
            .providerUserId(feignUserInfo.providerUserId())
            .email(feignUserInfo.email())
            .username(feignUserInfo.username())
            .build();
    }

    /**
     * redirectUri가 주어지지 않으면 설정값으로 대체한다.
     */
    protected String resolveRedirectUri(String redirectUri) {
        return redirectUri != null ? redirectUri : properties.getRedirectUri();
    }
}
