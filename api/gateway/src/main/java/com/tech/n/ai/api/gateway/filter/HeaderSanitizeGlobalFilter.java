package com.tech.n.ai.api.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 외부 요청의 x-user-* 헤더를 제거하여 스푸핑을 방지합니다.
 * JWT 필터보다 먼저 실행되어, JWT 필터가 검증 후 주입하는 헤더만 백엔드에 전달됩니다.
 */
@Component
public class HeaderSanitizeGlobalFilter implements GlobalFilter, Ordered {

    private static final List<String> SANITIZE_HEADERS = List.of(
        "x-user-id", "x-user-email", "x-user-role"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest sanitizedRequest = exchange.getRequest().mutate()
            .headers(headers -> SANITIZE_HEADERS.forEach(headers::remove))
            .build();
        return chain.filter(exchange.mutate().request(sanitizedRequest).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
