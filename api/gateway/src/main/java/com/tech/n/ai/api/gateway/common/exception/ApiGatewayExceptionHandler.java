package com.tech.n.ai.api.gateway.common.exception;

import com.tech.n.ai.common.core.constants.ErrorCodeConstants;
import com.tech.n.ai.common.core.dto.ApiResponse;
import com.tech.n.ai.common.core.dto.MessageCode;
import tools.jackson.databind.ObjectMapper;
import io.netty.channel.ConnectTimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;
import reactor.netty.channel.AbortedException;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

/**
 * Gateway 예외 처리 핸들러
 *
 * Spring Cloud Gateway는 Reactive 기반이므로 WebExceptionHandler 인터페이스를 구현합니다.
 * @ControllerAdvice는 사용할 수 없습니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(-2) // 가장 낮은 우선순위로 설정하여 다른 핸들러보다 먼저 실행
public class ApiGatewayExceptionHandler implements WebExceptionHandler {
    
    private final ObjectMapper objectMapper;
    
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        
        // HTTP 상태 코드 및 에러 코드 매핑
        HttpStatus httpStatus = determineHttpStatus(ex);
        String errorCode = determineErrorCode(httpStatus);
        String messageCode = determineMessageCode(httpStatus);
        String messageText = determineMessageText(httpStatus);
        
        response.setStatusCode(httpStatus);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        
        // 에러 로깅
        logError(ex, httpStatus, errorCode);
        
        // ApiResponse 형식의 에러 응답 생성
        MessageCode msgCode = new MessageCode(messageCode, messageText);
        ApiResponse<Void> errorResponse = ApiResponse.error(errorCode, msgCode);
        
        // JSON 응답 작성 (Reactive 방식)
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
    
    /**
     * 예외로부터 HTTP 상태 코드 결정
     * 
     * @param ex 예외
     * @return HTTP 상태 코드
     */
    private HttpStatus determineHttpStatus(Throwable ex) {
        if (ex instanceof ResponseStatusException statusException) {
            var statusCode = statusException.getStatusCode();
            if (statusCode instanceof HttpStatus) {
                return (HttpStatus) statusCode;
            }
            // HttpStatusCode를 HttpStatus로 변환
            int statusValue = statusCode.value();
            HttpStatus status = HttpStatus.resolve(statusValue);
            if (status != null) {
                return status;
            }
        }
        
        // 예외 타입에 따른 기본 상태 코드 매핑 (instanceof 타입 매칭)
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        if (ex instanceof TimeoutException || cause instanceof TimeoutException
                || ex instanceof io.netty.handler.timeout.ReadTimeoutException
                || cause instanceof io.netty.handler.timeout.ReadTimeoutException) {
            return HttpStatus.GATEWAY_TIMEOUT; // 504
        } else if (ex instanceof ConnectException || cause instanceof ConnectException
                || ex instanceof ConnectTimeoutException || cause instanceof ConnectTimeoutException
                || ex instanceof AbortedException || cause instanceof AbortedException) {
            return HttpStatus.BAD_GATEWAY; // 502
        }
        
        // 기본값: 내부 서버 오류
        return HttpStatus.INTERNAL_SERVER_ERROR; // 500
    }
    
    /**
     * HTTP 상태 코드로부터 에러 코드 결정
     * 
     * @param httpStatus HTTP 상태 코드
     * @return 에러 코드
     */
    private String determineErrorCode(HttpStatus httpStatus) {
        return switch (httpStatus) {
            case UNAUTHORIZED -> ErrorCodeConstants.AUTH_FAILED;
            case NOT_FOUND -> ErrorCodeConstants.NOT_FOUND;
            case BAD_GATEWAY -> ErrorCodeConstants.EXTERNAL_API_ERROR;
            case GATEWAY_TIMEOUT -> ErrorCodeConstants.TIMEOUT;
            default -> ErrorCodeConstants.INTERNAL_SERVER_ERROR;
        };
    }
    
    /**
     * HTTP 상태 코드로부터 메시지 코드 결정
     * 
     * @param httpStatus HTTP 상태 코드
     * @return 메시지 코드
     */
    private String determineMessageCode(HttpStatus httpStatus) {
        return switch (httpStatus) {
            case UNAUTHORIZED -> ErrorCodeConstants.MESSAGE_CODE_AUTH_FAILED;
            case NOT_FOUND -> ErrorCodeConstants.MESSAGE_CODE_NOT_FOUND;
            case BAD_GATEWAY -> ErrorCodeConstants.MESSAGE_CODE_EXTERNAL_API_ERROR;
            case GATEWAY_TIMEOUT -> ErrorCodeConstants.MESSAGE_CODE_TIMEOUT;
            default -> ErrorCodeConstants.MESSAGE_CODE_INTERNAL_SERVER_ERROR;
        };
    }
    
    /**
     * HTTP 상태 코드로부터 메시지 텍스트 결정
     * 
     * @param httpStatus HTTP 상태 코드
     * @return 메시지 텍스트
     */
    private String determineMessageText(HttpStatus httpStatus) {
        return switch (httpStatus) {
            case UNAUTHORIZED -> "인증에 실패했습니다.";
            case NOT_FOUND -> "요청한 경로를 찾을 수 없습니다.";
            case BAD_GATEWAY -> "백엔드 서버 연결에 실패했습니다.";
            case GATEWAY_TIMEOUT -> "백엔드 서버 응답 시간이 초과되었습니다.";
            default -> "서버 오류가 발생했습니다.";
        };
    }
    
    /**
     * 에러 로깅
     * 
     * @param ex 예외
     * @param httpStatus HTTP 상태 코드
     * @param errorCode 에러 코드
     */
    private void logError(Throwable ex, HttpStatus httpStatus, String errorCode) {
        if (httpStatus.is5xxServerError()) {
            log.error("Gateway error [{}]: {}", errorCode, ex.getMessage(), ex);
        } else if (httpStatus == HttpStatus.NOT_FOUND) {
            log.warn("Route not found [{}]: {}", errorCode, ex.getMessage());
        } else {
            log.warn("Gateway error [{}]: {}", errorCode, ex.getMessage());
        }
    }
}
