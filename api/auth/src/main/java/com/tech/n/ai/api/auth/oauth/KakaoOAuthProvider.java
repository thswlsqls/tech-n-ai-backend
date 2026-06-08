package com.tech.n.ai.api.auth.oauth;

import com.tech.n.ai.api.auth.config.OAuthProperties;
import com.tech.n.ai.client.feign.domain.oauth.contract.OAuthProviderContract;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component("KAKAO")
public class KakaoOAuthProvider extends AbstractOAuthProvider {

    public KakaoOAuthProvider(
            OAuthProperties.KakaoOAuthProperties kakaoProperties,
            @Qualifier("kakaoOAuthContract") OAuthProviderContract kakaoOAuthApi) {
        super(kakaoProperties, kakaoOAuthApi);
    }
}
