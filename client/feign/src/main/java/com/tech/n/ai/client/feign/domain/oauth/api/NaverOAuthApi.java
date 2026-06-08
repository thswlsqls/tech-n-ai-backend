package com.tech.n.ai.client.feign.domain.oauth.api;

import com.tech.n.ai.client.feign.domain.oauth.client.NaverOAuthFeignClient;
import com.tech.n.ai.client.feign.domain.oauth.client.NaverOAuthUserInfoFeignClient;
import com.tech.n.ai.client.feign.domain.oauth.contract.OAuthDto.NaverTokenResponse;
import com.tech.n.ai.client.feign.domain.oauth.contract.OAuthDto.NaverUserInfo;
import com.tech.n.ai.client.feign.domain.oauth.contract.OAuthDto.NaverUserInfoResponse;
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
public class NaverOAuthApi implements OAuthProviderContract {
    
    private final NaverOAuthFeignClient feignClient;
    private final NaverOAuthUserInfoFeignClient userInfoFeignClient;
    
    @Override
    public String exchangeAccessToken(String code, String clientId, String clientSecret, String redirectUri) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("code", code);
        
        NaverTokenResponse response = feignClient.exchangeToken(params);
        
        if (response == null || response.access_token() == null) {
            if (response != null && response.error() != null) {
                log.error("Naver OAuth Access Token 교환 실패: error={}, error_description={}", 
                    response.error(), response.error_description());
            } else {
                log.error("Naver OAuth Access Token 교환 실패: response={}", response);
            }
            throw new UnauthorizedException("Naver OAuth Access Token 교환에 실패했습니다.");
        }
        
        return response.access_token();
    }
    
    @Override
    public OAuthUserInfo getUserInfo(String accessToken) {
        String authorization = "Bearer " + accessToken;
        NaverUserInfoResponse response = userInfoFeignClient.getUserInfo(authorization);
        
        if (response == null || !"00".equals(response.resultcode())) {
            log.error("Naver 사용자 정보 조회 실패: resultcode={}, message={}", 
                response != null ? response.resultcode() : null,
                response != null ? response.message() : null);
            throw new UnauthorizedException("Naver 사용자 정보 조회에 실패했습니다.");
        }
        
        if (response.response() == null) {
            log.error("Naver 사용자 정보 응답이 null입니다: response={}", response);
            throw new UnauthorizedException("Naver 사용자 정보 조회에 실패했습니다.");
        }

        NaverUserInfo userInfo = response.response();
        String username = userInfo.name();
        if (StringUtils.isEmpty(username)) {
            username = userInfo.nickname();
        }
        if (StringUtils.isEmpty(username)) {
            username = userInfo.email();
        }

        return OAuthUserInfo.builder()
            .providerUserId(userInfo.id())
            .email(userInfo.email())
            .username(username)
            .build();
    }
}
