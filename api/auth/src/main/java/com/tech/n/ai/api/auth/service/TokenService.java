package com.tech.n.ai.api.auth.service;

import com.tech.n.ai.api.auth.dto.TokenResponse;
import com.tech.n.ai.common.security.jwt.JwtTokenPayload;
import com.tech.n.ai.common.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.tech.n.ai.api.auth.service.TokenConstants.*;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    public TokenResponse generateTokens(Long userId, String email, String role) {
        JwtTokenPayload payload = new JwtTokenPayload(
            String.valueOf(userId),
            email,
            role
        );

        String accessToken;
        String refreshToken;
        long accessTokenExpiry;
        long refreshTokenExpiry;

        if ("ADMIN".equals(role)) {
            accessToken = jwtTokenProvider.generateAdminAccessToken(payload);
            refreshToken = jwtTokenProvider.generateAdminRefreshToken(payload);
            accessTokenExpiry = ADMIN_ACCESS_TOKEN_EXPIRY_SECONDS;
            refreshTokenExpiry = ADMIN_REFRESH_TOKEN_EXPIRY_SECONDS;
            refreshTokenService.saveAdminRefreshToken(
                userId, refreshToken, jwtTokenProvider.getAdminRefreshTokenExpiresAt()
            );
        } else {
            accessToken = jwtTokenProvider.generateAccessToken(payload);
            refreshToken = jwtTokenProvider.generateRefreshToken(payload);
            accessTokenExpiry = ACCESS_TOKEN_EXPIRY_SECONDS;
            refreshTokenExpiry = REFRESH_TOKEN_EXPIRY_SECONDS;
            refreshTokenService.saveRefreshToken(
                userId, refreshToken, jwtTokenProvider.getRefreshTokenExpiresAt()
            );
        }

        return new TokenResponse(
            accessToken,
            refreshToken,
            TOKEN_TYPE,
            accessTokenExpiry,
            refreshTokenExpiry
        );
    }

    public JwtTokenPayload getPayloadFromToken(String token) {
        return jwtTokenProvider.getPayloadFromToken(token);
    }

    public boolean validateToken(String token) {
        return jwtTokenProvider.validateToken(token);
    }
}
