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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthService {
    
    private final ProviderReaderRepository providerReaderRepository;
    private final UserReaderRepository userReaderRepository;
    private final UserWriterRepository userWriterRepository;
    private final OAuthProviderFactory oauthProviderFactory;
    private final OAuthStateService oauthStateService;
    private final OAuthProperties oauthProperties;
    private final TokenService tokenService;
    
    public String startOAuthLogin(String providerName) {
        ProviderEntity provider = findAndValidateProvider(providerName);
        String state = SecureTokenGenerator.generate();
        
        oauthStateService.saveState(state, providerName.toUpperCase());
        
        OAuthProvider oauthProvider = oauthProviderFactory.getProvider(providerName);
        String redirectUri = getRedirectUri(providerName.toUpperCase());
        
        return oauthProvider.generateAuthorizationUrl(
            provider.getClientId(),
            redirectUri,
            state
        );
    }
    
    @Transactional
    public TokenResponse handleOAuthCallback(String providerName, String code, String state) {
        ProviderEntity provider = findAndValidateProvider(providerName);
        oauthStateService.validateAndDeleteState(state, providerName.toUpperCase());
        
        OAuthUserInfo userInfo = fetchOAuthUserInfo(providerName, code, provider);
        UserEntity user = findOrCreateUser(provider, userInfo);
        
        return tokenService.generateTokens(user.getId(), user.getEmail(), TokenConstants.USER_ROLE);
    }
    
    private ProviderEntity findAndValidateProvider(String providerName) {
        ProviderEntity provider = providerReaderRepository.findByName(providerName.toUpperCase())
            .orElseThrow(() -> new ResourceNotFoundException("지원하지 않는 OAuth 제공자입니다."));
        
        if (!Boolean.TRUE.equals(provider.getIsEnabled())) {
            throw new UnauthorizedException("비활성화된 OAuth 제공자입니다.");
        }
        
        return provider;
    }
    
    private OAuthUserInfo fetchOAuthUserInfo(String providerName, String code, ProviderEntity provider) {
        OAuthProvider oauthProvider = oauthProviderFactory.getProvider(providerName);
        String redirectUri = getRedirectUri(providerName.toUpperCase());
        
        String oauthAccessToken = oauthProvider.exchangeAccessToken(
            code,
            provider.getClientId(),
            provider.getClientSecret(),
            redirectUri
        );
        
        OAuthUserInfo userInfo = oauthProvider.getUserInfo(oauthAccessToken);
        
        if (userInfo == null) {
            throw new UnauthorizedException("사용자 정보를 가져올 수 없습니다.");
        }
        
        return userInfo;
    }
    
    private UserEntity findOrCreateUser(ProviderEntity provider, OAuthUserInfo userInfo) {
        Optional<UserEntity> userOpt = userReaderRepository.findByProviderIdAndProviderUserId(
            provider.getId(), userInfo.providerUserId());
        
        if (userOpt.isEmpty() || Boolean.TRUE.equals(userOpt.get().getIsDeleted())) {
            return createNewUser(provider, userInfo);
        }
        
        return updateExistingUser(userOpt.get(), userInfo);
    }
    
    private UserEntity createNewUser(ProviderEntity provider, OAuthUserInfo userInfo) {
        UserEntity user = UserEntity.createOAuthUser(
            userInfo.email(),
            userInfo.username(),
            provider.getId(),
            userInfo.providerUserId()
        );
        return userWriterRepository.save(user);
    }
    
    private UserEntity updateExistingUser(UserEntity user, OAuthUserInfo userInfo) {
        user.updateFromOAuth(userInfo.email(), userInfo.username());
        return userWriterRepository.save(user);
    }
    
    private String getRedirectUri(String providerName) {
        return switch (providerName) {
            case "GOOGLE" -> oauthProperties.getGoogle().getRedirectUri();
            case "NAVER" -> oauthProperties.getNaver().getRedirectUri();
            case "KAKAO" -> oauthProperties.getKakao().getRedirectUri();
            default -> throw new ResourceNotFoundException("지원하지 않는 OAuth 제공자입니다: " + providerName);
        };
    }
}
