package com.tech.n.ai.client.feign.domain.oauth.config;

import com.tech.n.ai.client.feign.domain.oauth.api.GoogleOAuthApi;
import com.tech.n.ai.client.feign.domain.oauth.api.KakaoOAuthApi;
import com.tech.n.ai.client.feign.domain.oauth.api.NaverOAuthApi;
import com.tech.n.ai.client.feign.domain.oauth.client.GoogleOAuthFeignClient;
import com.tech.n.ai.client.feign.domain.oauth.client.KakaoOAuthFeignClient;
import com.tech.n.ai.client.feign.domain.oauth.client.KakaoOAuthUserInfoFeignClient;
import com.tech.n.ai.client.feign.domain.oauth.client.NaverOAuthFeignClient;
import com.tech.n.ai.client.feign.domain.oauth.client.NaverOAuthUserInfoFeignClient;
import com.tech.n.ai.client.feign.domain.oauth.contract.OAuthProviderContract;
import com.tech.n.ai.client.feign.domain.oauth.mock.GoogleOAuthMock;
import com.tech.n.ai.client.feign.domain.oauth.mock.KakaoOAuthMock;
import com.tech.n.ai.client.feign.domain.oauth.mock.NaverOAuthMock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@EnableFeignClients(clients = {
    GoogleOAuthFeignClient.class,
    NaverOAuthFeignClient.class,
    NaverOAuthUserInfoFeignClient.class,
    KakaoOAuthFeignClient.class,
    KakaoOAuthUserInfoFeignClient.class
})
@Import({
    com.tech.n.ai.client.feign.config.OpenFeignConfig.class
})
@Configuration
public class OAuthFeignConfig {
    
    private static final String CLIENT_MODE = "feign-clients.oauth.mode";

    @Bean(name = "googleOAuthContract")
    @ConditionalOnProperty(name = CLIENT_MODE, havingValue = "mock")
    public OAuthProviderContract googleOAuthMock() {
        return new GoogleOAuthMock();
    }

    @Bean(name = "googleOAuthContract")
    @ConditionalOnProperty(name = CLIENT_MODE, havingValue = "rest")
    public OAuthProviderContract googleOAuthApi(GoogleOAuthFeignClient feignClient) {
        return new GoogleOAuthApi(feignClient);
    }

    @Bean(name = "naverOAuthContract")
    @ConditionalOnProperty(name = CLIENT_MODE, havingValue = "mock")
    public OAuthProviderContract naverOAuthMock() {
        return new NaverOAuthMock();
    }

    @Bean(name = "naverOAuthContract")
    @ConditionalOnProperty(name = CLIENT_MODE, havingValue = "rest")
    public OAuthProviderContract naverOAuthApi(NaverOAuthFeignClient feignClient, NaverOAuthUserInfoFeignClient userInfoFeignClient) {
        return new NaverOAuthApi(feignClient, userInfoFeignClient);
    }

    @Bean(name = "kakaoOAuthContract")
    @ConditionalOnProperty(name = CLIENT_MODE, havingValue = "mock")
    public OAuthProviderContract kakaoOAuthMock() {
        return new KakaoOAuthMock();
    }

    @Bean(name = "kakaoOAuthContract")
    @ConditionalOnProperty(name = CLIENT_MODE, havingValue = "rest")
    public OAuthProviderContract kakaoOAuthApi(KakaoOAuthFeignClient feignClient, KakaoOAuthUserInfoFeignClient userInfoFeignClient) {
        return new KakaoOAuthApi(feignClient, userInfoFeignClient);
    }
}
