package com.tech.n.ai.client.feign.domain.oauth.api;

import com.tech.n.ai.client.feign.domain.oauth.client.KakaoOAuthFeignClient;
import com.tech.n.ai.client.feign.domain.oauth.client.KakaoOAuthUserInfoFeignClient;
import com.tech.n.ai.client.feign.domain.oauth.contract.OAuthDto.KakaoTokenResponse;
import com.tech.n.ai.client.feign.domain.oauth.contract.OAuthDto.KakaoUserInfoResponse;
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
public class KakaoOAuthApi implements OAuthProviderContract {
    
    private final KakaoOAuthFeignClient feignClient;
    private final KakaoOAuthUserInfoFeignClient userInfoFeignClient;
    
    @Override
    public String exchangeAccessToken(String code, String clientId, String clientSecret, String redirectUri) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", clientId);
        params.add("redirect_uri", redirectUri);
        params.add("code", code);
        if (StringUtils.isNotEmpty(clientSecret)) {
            params.add("client_secret", clientSecret);
        }
        
        KakaoTokenResponse response = feignClient.exchangeToken(params);
        
        if (response == null || response.access_token() == null) {
            log.error("Kakao OAuth Access Token 교환 실패: response={}", response);
            throw new UnauthorizedException("Kakao OAuth Access Token 교환에 실패했습니다.");
        }
        
        return response.access_token();
    }
    
    @Override
    public OAuthUserInfo getUserInfo(String accessToken) {
        String authorization = "Bearer " + accessToken;
        KakaoUserInfoResponse response = userInfoFeignClient.getUserInfo(authorization);
        
        if (response == null || response.id() == null) {
            log.error("Kakao 사용자 정보 조회 실패: response={}", response);
            throw new UnauthorizedException("Kakao 사용자 정보 조회에 실패했습니다.");
        }
        
        String email = null;
        String username = null;
        
        if (response.kakao_account() != null) {
            email = response.kakao_account().email();
            if (response.kakao_account().profile() != null) {
                username = response.kakao_account().profile().nickname();
            }
        }
        
        if (StringUtils.isEmpty(username)) {
            if (response.properties() != null) {
                username = response.properties().nickname();
            }
        }
        
        if (StringUtils.isEmpty(username)) {
            username = email != null ? email : "KakaoUser_" + response.id();
        }
        
        return OAuthUserInfo.builder()
            .providerUserId(String.valueOf(response.id()))
            .email(email)
            .username(username)
            .build();
    }
}
