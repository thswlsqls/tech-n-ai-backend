package com.tech.n.ai.common.exception.logging;

import org.springframework.data.mongodb.core.mapping.Field;

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
    @Field("source") String source,
    
    /**
     * 예외 타입 (예: "DataAccessException", "ValidationException")
     */
    @Field("exception_type") String exceptionType,
    
    /**
     * 예외 메시지
     */
    @Field("exception_message") String exceptionMessage,
    
    /**
     * 스택 트레이스 (전체)
     */
    @Field("stack_trace") String stackTrace,
    
    /**
     * 컨텍스트 정보
     */
    @Field("context") ContextInfo context,
    
    /**
     * 발생 일시
     */
    @Field("occurred_at") Instant occurredAt,
    
    /**
     * 심각도: "LOW", "MEDIUM", "HIGH", "CRITICAL"
     */
    @Field("severity") String severity
) {
    /**
     * 컨텍스트 정보 내부 클래스
     */
    public record ContextInfo(
        /**
         * 모듈명
         */
        @Field("module") String module,
        
        /**
         * 메서드명
         */
        @Field("method") String method,
        
        /**
         * 파라미터 정보
         */
        @Field("parameters") Map<String, Object> parameters,
        
        /**
         * 요청 URI (nullable)
         */
        @Field("request_uri") String requestUri,

        /**
         * 사용자 ID (nullable)
         */
        @Field("user_id") String userId,

        /**
         * 요청 ID (nullable)
         */
        @Field("request_id") String requestId
    ) {
    }
}

