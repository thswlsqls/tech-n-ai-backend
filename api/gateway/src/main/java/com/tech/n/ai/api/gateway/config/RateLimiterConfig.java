package com.tech.n.ai.api.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Configuration
public class RateLimiterConfig {

    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(resolveRemoteIp(exchange));
    }

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("x-user-id");
            if (userId != null) {
                return Mono.just(userId);
            }
            return Mono.just(resolveRemoteIp(exchange));
        };
    }

    private String resolveRemoteIp(ServerWebExchange exchange) {
        return Optional.ofNullable(exchange.getRequest().getRemoteAddress())
            .map(addr -> addr.getAddress().getHostAddress())
            .orElse("unknown");
    }
}
