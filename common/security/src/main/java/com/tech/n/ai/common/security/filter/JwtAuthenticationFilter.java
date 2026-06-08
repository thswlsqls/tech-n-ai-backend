package com.tech.n.ai.common.security.filter;

import com.tech.n.ai.common.core.constants.ErrorCodeConstants;
import com.tech.n.ai.common.security.jwt.JwtTokenPayload;
import com.tech.n.ai.common.security.jwt.JwtTokenProvider;
import com.tech.n.ai.common.security.principal.UserPrincipal;
import com.tech.n.ai.common.security.support.SecurityErrorResponseWriter;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT 인증 필터
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String token = extractToken(request);

        if (!StringUtils.hasText(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            JwtTokenPayload payload = jwtTokenProvider.getPayloadFromToken(token);
            setSecurityContext(payload, request);
        } catch (Exception e) {
            log.warn("JWT 인증 실패: {}", e.getMessage());
            writeUnauthorizedResponse(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void setSecurityContext(JwtTokenPayload payload, HttpServletRequest request) {
        UserPrincipal userPrincipal = UserPrincipal.of(payload.userId(), payload.email(), payload.role());

        var authentication = new UsernamePasswordAuthenticationToken(
            userPrincipal,
            null,
            Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + payload.role()))
        );
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private void writeUnauthorizedResponse(HttpServletResponse response) throws IOException {
        SecurityErrorResponseWriter.writeError(
            objectMapper, response,
            HttpServletResponse.SC_UNAUTHORIZED,
            ErrorCodeConstants.AUTH_FAILED,
            ErrorCodeConstants.MESSAGE_CODE_AUTH_FAILED,
            "인증에 실패했습니다."
        );
    }
}
