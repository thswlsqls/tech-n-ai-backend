package com.tech.n.ai.api.gateway.filter;

import com.tech.n.ai.common.core.constants.ApiConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Optional;

/**
 * 구조화된 Access Log를 출력하는 GlobalFilter.
 * 필터 체인 가장 마지막에서 실행되어 응답 시간을 정확히 측정합니다.
 */
@Component
public class AccessLogGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("ACCESS_LOG");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.nanoTime();

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            ServerHttpRequest request = exchange.getRequest();
            ServerHttpResponse response = exchange.getResponse();

            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            String method = request.getMethod().name();
            String path = request.getURI().getPath();
            int statusCode = Optional.ofNullable(response.getStatusCode())
                .map(s -> s.value())
                .orElse(0);
            String requestId = Optional.ofNullable(request.getHeaders().getFirst(ApiConstants.HEADER_X_REQUEST_ID))
                .orElse("-");
            String clientIp = extractClientIp(request);
            String userId = Optional.ofNullable(request.getHeaders().getFirst("x-user-id"))
                .orElse("-");
            String userAgent = Optional.ofNullable(request.getHeaders().getFirst("User-Agent"))
                .orElse("-");
            String routeId = Optional.ofNullable(exchange.<Route>getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR))
                .map(Route::getId)
                .orElse("-");

            ACCESS_LOG.info("{} {} {} {}ms requestId={} clientIp={} userId={} route={} userAgent={}",
                method, path, statusCode, durationMs, requestId, clientIp, userId, routeId, userAgent);
        }));
    }

    private String extractClientIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return Optional.ofNullable(request.getRemoteAddress())
            .map(InetSocketAddress::getAddress)
            .map(addr -> addr.getHostAddress())
            .orElse("unknown");
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
