package com.tech.n.ai.api.gateway.filter;

import com.tech.n.ai.common.core.constants.ApiConstants;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * X-Request-Id 헤더를 발급/전파하여 게이트웨이-백엔드 간 요청을 추적합니다.
 * 클라이언트가 유효한 UUID 형식의 X-Request-Id를 보내면 그대로 전파하고,
 * 없거나 유효하지 않으면 새로 생성합니다.
 */
@Component
public class RequestIdGlobalFilter implements GlobalFilter, Ordered {

    private static final String REQUEST_ID_HEADER = ApiConstants.HEADER_X_REQUEST_ID;
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
        Pattern.CASE_INSENSITIVE
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String requestId = request.getHeaders().getFirst(REQUEST_ID_HEADER);

        if (requestId == null || !UUID_PATTERN.matcher(requestId).matches()) {
            requestId = UUID.randomUUID().toString();
        }

        ServerHttpRequest modifiedRequest = request.mutate()
            .header(REQUEST_ID_HEADER, requestId)
            .build();

        ServerWebExchange modifiedExchange = exchange.mutate()
            .request(modifiedRequest)
            .build();

        String finalRequestId = requestId;
        modifiedExchange.getResponse().beforeCommit(() -> {
            if (modifiedExchange.getResponse().getHeaders().getFirst(REQUEST_ID_HEADER) == null) {
                modifiedExchange.getResponse().getHeaders()
                    .add(REQUEST_ID_HEADER, finalRequestId);
            }
            return Mono.empty();
        });
        return chain.filter(modifiedExchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
