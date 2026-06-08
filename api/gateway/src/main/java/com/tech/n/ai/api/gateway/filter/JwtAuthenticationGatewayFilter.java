package com.tech.n.ai.api.gateway.filter;

import com.tech.n.ai.api.gateway.config.GatewaySecurityProperties;
import com.tech.n.ai.common.core.constants.ErrorCodeConstants;
import com.tech.n.ai.common.core.dto.ApiResponse;
import com.tech.n.ai.common.core.dto.MessageCode;
import com.tech.n.ai.common.security.jwt.JwtTokenPayload;
import com.tech.n.ai.common.security.jwt.JwtTokenProvider;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JWT 인증 Gateway Filter
 *
 * 인증이 필요한 경로에 대해 JWT 토큰을 검증하고, 사용자 정보를 헤더에 주입합니다.
 * 경로 분류(공개/보호/관리자)는 {@link GatewaySecurityProperties}에서 외부화하여 관리합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationGatewayFilter implements GatewayFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String USER_ID_HEADER = "x-user-id";
    private static final String USER_EMAIL_HEADER = "x-user-email";
    private static final String USER_ROLE_HEADER = "x-user-role";

    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;
    private final GatewaySecurityProperties securityProperties;

    private List<PathPattern> publicPathPatterns;
    private List<PathPattern> publicPathExclusionPatterns;
    private List<PathPattern> adminOnlyPathPatterns;

    @PostConstruct
    void init() {
        PathPatternParser parser = new PathPatternParser();
        publicPathPatterns = securityProperties.publicPaths().stream()
            .map(parser::parse)
            .toList();
        publicPathExclusionPatterns = securityProperties.publicPathExclusions().stream()
            .map(parser::parse)
            .toList();
        adminOnlyPathPatterns = securityProperties.adminOnlyPaths().stream()
            .map(parser::parse)
            .toList();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // 인증 불필요 경로 확인
        String path = request.getURI().getPath();
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // JWT 토큰 추출
        String token = extractToken(request);
        if (token == null) {
            log.debug("JWT token not found for path: {}", path);
            return handleUnauthorized(exchange);
        }

        // JWT 토큰 검증
        if (!jwtTokenProvider.validateToken(token)) {
            log.debug("Invalid JWT token for path: {}", path);
            return handleUnauthorized(exchange);
        }

        // 사용자 정보 추출 및 헤더 주입
        try {
            JwtTokenPayload payload = jwtTokenProvider.getPayloadFromToken(token);

            // 관리자 전용 경로 검증
            if (isAdminOnlyPath(path) && !"ADMIN".equals(payload.role())) {
                return handleForbidden(exchange);
            }

            ServerHttpRequest modifiedRequest = request.mutate()
                .header(USER_ID_HEADER, payload.userId())
                .header(USER_EMAIL_HEADER, payload.email())
                .header(USER_ROLE_HEADER, payload.role())
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .build();

            log.debug("JWT authentication successful for user: {}", payload.userId());
            return chain.filter(exchange.mutate().request(modifiedRequest).build());
        } catch (Exception e) {
            log.error("Error extracting JWT payload for path: {}", path, e);
            return handleUnauthorized(exchange);
        }
    }

    /**
     * 인증 불필요 경로 확인
     *
     * 경로 우선순위: 구체적 inclusion(와일드카드 없음) > exclusion > 와일드카드 inclusion
     * 구체적 경로가 inclusion에 정확히 매칭되면 exclusion보다 우선합니다.
     */
    private boolean isPublicPath(String path) {
        org.springframework.http.server.PathContainer pathContainer =
            org.springframework.http.server.PathContainer.parsePath(path);

        // 1. 구체적 inclusion 패턴(와일드카드 없음)에 매칭되면 즉시 공개 경로
        for (PathPattern pattern : publicPathPatterns) {
            if (!pattern.hasPatternSyntax() && pattern.matches(pathContainer)) {
                return true;
            }
        }

        // 2. exclusion 패턴에 매칭되면 공개 경로가 아님
        for (PathPattern exclusion : publicPathExclusionPatterns) {
            if (exclusion.matches(pathContainer)) {
                return false;
            }
        }

        // 3. 와일드카드 inclusion 패턴에 매칭되면 공개 경로
        for (PathPattern pattern : publicPathPatterns) {
            if (pattern.matches(pathContainer)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Authorization 헤더에서 Bearer 토큰 추출
     */
    private String extractToken(ServerHttpRequest request) {
        String bearerToken = request.getHeaders().getFirst(AUTHORIZATION_HEADER);
        if (bearerToken != null && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    /**
     * 관리자 전용 경로 확인
     */
    private boolean isAdminOnlyPath(String path) {
        org.springframework.http.server.PathContainer pathContainer =
            org.springframework.http.server.PathContainer.parsePath(path);

        for (PathPattern pattern : adminOnlyPathPatterns) {
            if (pattern.matches(pathContainer)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 권한 부족 시 403 Forbidden 응답 반환
     */
    private Mono<Void> handleForbidden(ServerWebExchange exchange) {
        return writeErrorResponse(
            exchange,
            HttpStatus.FORBIDDEN,
            ErrorCodeConstants.FORBIDDEN,
            ErrorCodeConstants.MESSAGE_CODE_FORBIDDEN,
            "권한이 없습니다."
        );
    }

    /**
     * 인증 실패 시 401 Unauthorized 응답 반환
     */
    private Mono<Void> handleUnauthorized(ServerWebExchange exchange) {
        return writeErrorResponse(
            exchange,
            HttpStatus.UNAUTHORIZED,
            ErrorCodeConstants.AUTH_FAILED,
            ErrorCodeConstants.MESSAGE_CODE_AUTH_FAILED,
            "인증에 실패했습니다."
        );
    }

    /**
     * ApiResponse 형식의 에러 응답을 JSON으로 작성해 반환
     */
    private Mono<Void> writeErrorResponse(
        ServerWebExchange exchange,
        HttpStatus status,
        String errorCode,
        String messageCodeValue,
        String messageText
    ) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        MessageCode messageCode = new MessageCode(messageCodeValue, messageText);
        ApiResponse<Void> errorResponse = ApiResponse.error(errorCode, messageCode);

        DataBufferFactory bufferFactory = response.bufferFactory();
        try {
            String jsonResponse = objectMapper.writeValueAsString(errorResponse);
            DataBuffer buffer = bufferFactory.wrap(jsonResponse.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            log.error("Error writing error response", e);
            return response.setComplete();
        }
    }
}
