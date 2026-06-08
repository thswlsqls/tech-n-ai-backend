package com.tech.n.ai.api.auth.oauth;

import com.tech.n.ai.api.auth.config.OAuthProperties;
import com.tech.n.ai.client.feign.domain.oauth.contract.OAuthProviderContract;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component("GOOGLE")
public class GoogleOAuthProvider extends AbstractOAuthProvider {

    private final OAuthProperties.GoogleOAuthProperties googleProperties;

    public GoogleOAuthProvider(
            OAuthProperties.GoogleOAuthProperties googleProperties,
            @Qualifier("googleOAuthContract") OAuthProviderContract googleOAuthApi) {
        super(googleProperties, googleOAuthApi);
        this.googleProperties = googleProperties;
    }

    @Override
    public String generateAuthorizationUrl(String clientId, String redirectUri, String state) {
        return UriComponentsBuilder
            .fromUriString(googleProperties.getAuthorizationEndpoint())
            .queryParam("client_id", clientId)
            .queryParam("redirect_uri", redirectUri != null ? redirectUri : googleProperties.getRedirectUri())
            .queryParam("response_type", "code")
            .queryParam("scope", googleProperties.getScope())
            .queryParam("state", state)
            .queryParam("access_type", "online")
            .build()
            .toUriString();
    }
}
