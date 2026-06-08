package com.tech.n.ai.client.feign.domain.oauth.api;

import com.tech.n.ai.client.feign.domain.oauth.client.GoogleOAuthFeignClient;
import com.tech.n.ai.client.feign.domain.oauth.contract.OAuthDto.GoogleTokenResponse;
import com.tech.n.ai.client.feign.domain.oauth.contract.OAuthDto.GoogleUserInfoResponse;
import com.tech.n.ai.client.feign.domain.oauth.contract.OAuthDto.OAuthUserInfo;
import com.tech.n.ai.client.feign.domain.oauth.contract.OAuthProviderContract;
import com.tech.n.ai.common.core.util.StringUtils;
import com.tech.n.ai.common.exception.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Slf4j
@RequiredArgsConstructor
public class GoogleOAuthApi implements OAuthProviderContract {
    
    private final GoogleOAuthFeignClient feignClient;
    
    @Override
    public String exchangeAccessToken(String code, String clientId, String clientSecret, String redirectUri) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("redirect_uri", redirectUri);
        params.add("grant_type", "authorization_code");
        
        GoogleTokenResponse response = feignClient.exchangeToken(params);
        
        if (response == null || response.access_token() == null) {
            log.error("Google OAuth Access Token 교환 실패: response={}", response);
            throw new UnauthorizedException("Google OAuth Access Token 교환에 실패했습니다.");
        }
        
        return response.access_token();
    }
    
    @Override
    public OAuthUserInfo getUserInfo(String accessToken) {
        String authorization = "Bearer " + accessToken;
        GoogleUserInfoResponse response = feignClient.getUserInfo(authorization);
        
        if (response == null || response.id() == null) {
            log.error("Google 사용자 정보 조회 실패: response={}", response);
            throw new UnauthorizedException("Google 사용자 정보 조회에 실패했습니다.");
        }
        
        String username = response.name();
        if (StringUtils.isEmpty(username)) {
            username = response.email();
        }
        
        return OAuthUserInfo.builder()
            .providerUserId(response.id())
            .email(response.email())
            .username(username)
            .build();
    }
}
