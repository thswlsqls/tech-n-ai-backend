package com.tech.n.ai.common.exception.logging;

import java.time.Instant;
import java.util.Map;

/**
 * 예외 발생 컨텍스트 정보
 * ExceptionLogDocument 구조와 일치하도록 구현
 */
public record ExceptionContext(
    /**
     * 예외 소스: "READ" 또는 "WRITE"
     */
    String source,
    
    /**
     * 예외 타입 (예: "DataAccessException", "ValidationException")
     */
    String exceptionType,
    
    /**
     * 예외 메시지
     */
    String exceptionMessage,
    
    /**
     * 스택 트레이스 (전체)
     */
    String stackTrace,
    
    /**
     * 컨텍스트 정보
     */
    ContextInfo context,
    
    /**
     * 발생 일시
     */
    Instant occurredAt,
    
    /**
     * 심각도: "LOW", "MEDIUM", "HIGH", "CRITICAL"
     */
    String severity
) {
    /**
     * 컨텍스트 정보 내부 클래스
     */
    public record ContextInfo(
        /**
         * 모듈명
         */
        String module,
        
        /**
         * 메서드명
         */
        String method,
        
        /**
         * 파라미터 정보
         */
        Map<String, Object> parameters,
        
        /**
         * 요청 URI (nullable)
         */
        String requestUri,

        /**
         * 사용자 ID (nullable)
         */
        String userId,

        /**
         * 요청 ID (nullable)
         */
        String requestId
    ) {
    }
}

