package com.tech.n.ai.api.auth.oauth;

import com.tech.n.ai.api.auth.config.OAuthProperties;
import com.tech.n.ai.client.feign.domain.oauth.contract.OAuthProviderContract;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component("NAVER")
public class NaverOAuthProvider extends AbstractOAuthProvider {

    public NaverOAuthProvider(
            OAuthProperties.NaverOAuthProperties naverProperties,
            @Qualifier("naverOAuthContract") OAuthProviderContract naverOAuthApi) {
        super(naverProperties, naverOAuthApi);
    }
}
