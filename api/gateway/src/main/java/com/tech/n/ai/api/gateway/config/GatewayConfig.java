package com.tech.n.ai.api.gateway.config;

import com.tech.n.ai.api.gateway.filter.JwtAuthenticationGatewayFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import reactor.core.publisher.Mono;

/**
 * Gateway 설정 클래스
 *
 * Spring Cloud Gateway의 라우팅 및 필터 설정을 관리합니다.
 */
@Configuration
@RequiredArgsConstructor
public class GatewayConfig {
    
    private final JwtAuthenticationGatewayFilter jwtAuthenticationGatewayFilter;
    
    /**
     * JWT 인증 필터를 GlobalFilter로 등록
     * 
     * 모든 Route에 대해 JWT 인증 필터가 적용됩니다.
     * 인증 불필요 경로는 필터 내부에서 처리됩니다.
     * 
     * @return GlobalFilter
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 2)
    public GlobalFilter jwtAuthenticationGlobalFilter() {
        return (exchange, chain) -> {
            // JwtAuthenticationGatewayFilter를 GlobalFilter로 래핑
            return jwtAuthenticationGatewayFilter.filter(exchange, chain);
        };
    }
}
