package com.tech.n.ai.common.exception.handler;

import com.tech.n.ai.common.core.constants.ErrorCodeConstants;
import com.tech.n.ai.common.core.dto.ApiResponse;
import com.tech.n.ai.common.core.dto.MessageCode;
import com.tech.n.ai.common.core.exception.BaseException;
import com.tech.n.ai.common.core.exception.BusinessException;
import com.tech.n.ai.common.exception.exception.ConflictException;
import com.tech.n.ai.common.exception.exception.ExternalApiException;
import com.tech.n.ai.common.exception.exception.ForbiddenException;
import com.tech.n.ai.common.exception.exception.RateLimitExceededException;
import com.tech.n.ai.common.exception.exception.ResourceNotFoundException;
import com.tech.n.ai.common.exception.exception.UnauthorizedException;
import com.tech.n.ai.common.exception.logging.ExceptionContext;
import com.tech.n.ai.common.exception.logging.ExceptionLoggingService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 전역 예외 처리 핸들러
 * 모든 예외를 일관된 형식으로 처리
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {
    
    private final ExceptionLoggingService exceptionLoggingService;
    
    /**
     * BaseException 처리 (모든 커스텀 예외의 부모)
     */
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiResponse<Void>> handleBaseException(BaseException e, HttpServletRequest request) {
        logException(e, request, determineSource(e));
        
        // 예외 메시지가 있으면 상세 메시지 사용, 없으면 기본 메시지 사용
        String text = (e.getMessage() != null && !e.getMessage().isBlank())
            ? e.getMessage()
            : getMessageText(e.getMessageCode());
        MessageCode messageCode = new MessageCode(e.getMessageCode(), text);
        ApiResponse<Void> response = ApiResponse.error(e.getErrorCode(), messageCode);
        
        HttpStatus httpStatus = mapErrorCodeToHttpStatus(e.getErrorCode());
        return ResponseEntity.status(httpStatus).body(response);
    }
    
    /**
     * ResourceNotFoundException 처리
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(
        ResourceNotFoundException e, HttpServletRequest request) {
        return handleBaseException(e, request);
    }
    
    /**
     * UnauthorizedException 처리
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorizedException(
        UnauthorizedException e, HttpServletRequest request) {
        return handleBaseException(e, request);
    }
    
    /**
     * ForbiddenException 처리
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbiddenException(
        ForbiddenException e, HttpServletRequest request) {
        return handleBaseException(e, request);
    }
    
    /**
     * RateLimitExceededException 처리
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimitExceededException(
        RateLimitExceededException e, HttpServletRequest request) {
        return handleBaseException(e, request);
    }
    
    /**
     * ExternalApiException 처리
     */
    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleExternalApiException(
        ExternalApiException e, HttpServletRequest request) {
        return handleBaseException(e, request);
    }
    
    /**
     * ConflictException 처리 - ValidationException과 동일한 형식
     */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleConflictException(
        ConflictException e, HttpServletRequest request) {
        logException(e, request, "READ");
        
        Map<String, String> errors = new HashMap<>();
        errors.put(e.getFieldName(), e.getMessage());
        
        MessageCode messageCode = new MessageCode(
            ErrorCodeConstants.MESSAGE_CODE_VALIDATION_ERROR,
            "유효성 검증에 실패했습니다."
        );
        
        ApiResponse<Map<String, String>> response = new ApiResponse<>(
            ErrorCodeConstants.VALIDATION_ERROR,
            messageCode,
            null,
            errors
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * 유효성 검증 예외 처리
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(
        MethodArgumentNotValidException e, HttpServletRequest request) {
        logException(e, request, "READ");
        
        Map<String, String> errors = e.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                fieldError -> fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "유효성 검증 실패",
                (existing, replacement) -> existing
            ));
        
        MessageCode messageCode = new MessageCode(
            ErrorCodeConstants.MESSAGE_CODE_VALIDATION_ERROR,
            "유효성 검증에 실패했습니다."
        );
        ApiResponse<Map<String, String>> response = new ApiResponse<>(
            ErrorCodeConstants.VALIDATION_ERROR,
            messageCode,
            null,
            errors
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * 쿼리 파라미터 유효성 검증 예외 처리 (Spring Boot 3.x)
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleHandlerMethodValidationException(
        HandlerMethodValidationException e, HttpServletRequest request) {
        logException(e, request, "READ");

        Map<String, String> errors = new HashMap<>();
        e.getParameterValidationResults().forEach(result ->
            result.getResolvableErrors().forEach(error -> {
                String paramName = result.getMethodParameter().getParameterName();
                String message = error.getDefaultMessage() != null ? error.getDefaultMessage() : "유효성 검증 실패";
                String key = paramName != null ? paramName : "param" + result.getMethodParameter().getParameterIndex();
                errors.put(key, message);
            })
        );

        MessageCode messageCode = new MessageCode(
            ErrorCodeConstants.MESSAGE_CODE_VALIDATION_ERROR,
            "유효성 검증에 실패했습니다."
        );
        ApiResponse<Map<String, String>> response = new ApiResponse<>(
            ErrorCodeConstants.VALIDATION_ERROR,
            messageCode,
            null,
            errors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 파라미터 타입 불일치 예외 처리 (예: page=abc)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleTypeMismatchException(
        MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        logException(e, request, "READ");

        Map<String, String> errors = new HashMap<>();
        String paramName = e.getName();
        String requiredType = e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "알 수 없음";
        errors.put(paramName, String.format("'%s' 값은 %s 타입이어야 합니다.", e.getValue(), requiredType));

        MessageCode messageCode = new MessageCode(
            ErrorCodeConstants.MESSAGE_CODE_VALIDATION_ERROR,
            "유효성 검증에 실패했습니다."
        );
        ApiResponse<Map<String, String>> response = new ApiResponse<>(
            ErrorCodeConstants.VALIDATION_ERROR,
            messageCode,
            null,
            errors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 필수 파라미터 누락 예외 처리
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleMissingParameterException(
        MissingServletRequestParameterException e, HttpServletRequest request) {
        logException(e, request, "READ");

        Map<String, String> errors = new HashMap<>();
        errors.put(e.getParameterName(), String.format("'%s' 파라미터는 필수입니다.", e.getParameterName()));

        MessageCode messageCode = new MessageCode(
            ErrorCodeConstants.MESSAGE_CODE_VALIDATION_ERROR,
            "유효성 검증에 실패했습니다."
        );
        ApiResponse<Map<String, String>> response = new ApiResponse<>(
            ErrorCodeConstants.VALIDATION_ERROR,
            messageCode,
            null,
            errors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * NoResourceFoundException 처리 - 존재하지 않는 리소스 요청
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(
        NoResourceFoundException e, HttpServletRequest request) {
        log.warn("Resource not found: {} {}", e.getHttpMethod(), e.getResourcePath());

        MessageCode messageCode = new MessageCode(
            ErrorCodeConstants.MESSAGE_CODE_NOT_FOUND,
            "요청한 리소스를 찾을 수 없습니다."
        );
        ApiResponse<Void> response = ApiResponse.error(
            ErrorCodeConstants.NOT_FOUND,
            messageCode
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * HTTP Method 불일치 예외 처리 (GET, PUT, DELETE, PATCH → POST only 엔드포인트)
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(
        HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        log.warn("Method not allowed: {} {}", request.getMethod(), request.getRequestURI());

        MessageCode messageCode = new MessageCode(
            ErrorCodeConstants.MESSAGE_CODE_METHOD_NOT_ALLOWED,
            "허용되지 않는 HTTP 메서드입니다."
        );
        ApiResponse<Void> response = ApiResponse.error(
            ErrorCodeConstants.METHOD_NOT_ALLOWED,
            messageCode
        );

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
    }

    /**
     * Content-Type 불일치 예외 처리 (text/plain 등 → application/json 필요)
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(
        HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
        log.warn("Unsupported media type: {} for {} {}", e.getContentType(), request.getMethod(), request.getRequestURI());

        MessageCode messageCode = new MessageCode(
            ErrorCodeConstants.MESSAGE_CODE_UNSUPPORTED_MEDIA_TYPE,
            "지원하지 않는 Content-Type입니다."
        );
        ApiResponse<Void> response = ApiResponse.error(
            ErrorCodeConstants.UNSUPPORTED_MEDIA_TYPE,
            messageCode
        );

        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(response);
    }

    /**
     * 요청 본문 파싱 실패 예외 처리 (빈 body, 잘못된 JSON 등)
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(
        HttpMessageNotReadableException e, HttpServletRequest request) {
        log.warn("Message not readable: {} {}", request.getMethod(), request.getRequestURI());

        MessageCode messageCode = new MessageCode(
            ErrorCodeConstants.MESSAGE_CODE_BAD_REQUEST,
            "요청 본문을 읽을 수 없습니다."
        );
        ApiResponse<Void> response = ApiResponse.error(
            ErrorCodeConstants.BAD_REQUEST,
            messageCode
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 필수 요청 헤더 누락 예외 처리 (x-user-id 등)
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestHeader(
        MissingRequestHeaderException e, HttpServletRequest request) {
        log.warn("Missing required header '{}': {} {}", e.getHeaderName(), request.getMethod(), request.getRequestURI());

        MessageCode messageCode = new MessageCode(
            ErrorCodeConstants.MESSAGE_CODE_BAD_REQUEST,
            String.format("필수 헤더 '%s'가 누락되었습니다.", e.getHeaderName())
        );
        ApiResponse<Void> response = ApiResponse.error(
            ErrorCodeConstants.BAD_REQUEST,
            messageCode
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 데이터 무결성 위반 예외 처리 (unique constraint 등)
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleDataIntegrityViolationException(
        DataIntegrityViolationException e, HttpServletRequest request) {
        log.warn("Data integrity violation: {}", e.getMostSpecificCause().getMessage());
        logException(e, request, "WRITE");

        Map<String, String> errors = new HashMap<>();
        errors.put("field", "데이터 무결성 제약 조건을 위반했습니다.");

        MessageCode messageCode = new MessageCode(
            ErrorCodeConstants.MESSAGE_CODE_VALIDATION_ERROR,
            "유효성 검증에 실패했습니다."
        );
        ApiResponse<Map<String, String>> response = new ApiResponse<>(
            ErrorCodeConstants.VALIDATION_ERROR,
            messageCode,
            null,
            errors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 예상치 못한 예외 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e, HttpServletRequest request) {
        log.error("Unexpected error occurred", e);
        logException(e, request, "READ");
        
        MessageCode messageCode = new MessageCode(
            ErrorCodeConstants.MESSAGE_CODE_INTERNAL_SERVER_ERROR,
            "내부 서버 오류가 발생했습니다."
        );
        ApiResponse<Void> response = ApiResponse.error(
            ErrorCodeConstants.INTERNAL_SERVER_ERROR,
            messageCode
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    /**
     * 예외 로깅
     */
    private void logException(Exception exception, HttpServletRequest request, String source) {
        try {
            ExceptionContext.ContextInfo context = buildContextInfo(request);
            
            if ("READ".equals(source)) {
                exceptionLoggingService.logReadException(exception, context);
            } else {
                exceptionLoggingService.logWriteException(exception, context);
            }
        } catch (Exception e) {
            log.error("Failed to log exception", e);
        }
    }
    
    /**
     * 컨텍스트 정보 생성
     */
    private ExceptionContext.ContextInfo buildContextInfo(HttpServletRequest request) {
        String[] pathSegments = request.getRequestURI().split("/");
        String module = pathSegments.length > 1 ? pathSegments[1] : "unknown";
        String method = request.getMethod();
        Map<String, Object> parameters = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values.length == 1) {
                parameters.put(key, values[0]);
            } else {
                parameters.put(key, values);
            }
        });
        
        String requestUri = request.getRequestURI();
        String userId = request.getHeader("X-User-Id");
        String requestId = request.getHeader("X-Request-Id");

        return new ExceptionContext.ContextInfo(
            module,
            method,
            parameters,
            requestUri,
            userId,
            requestId
        );
    }
    
    /**
     * 예외 소스 결정 (READ 또는 WRITE)
     */
    private String determineSource(BaseException e) {
        // ExternalApiException은 WRITE로 간주 (외부 API 호출)
        if (e instanceof ExternalApiException) {
            return "WRITE";
        }
        // 기본적으로 READ로 간주
        return "READ";
    }
    
    /**
     * 에러 코드를 HTTP 상태 코드로 매핑
     */
    private HttpStatus mapErrorCodeToHttpStatus(String errorCode) {
        return switch (errorCode) {
            case ErrorCodeConstants.BAD_REQUEST, ErrorCodeConstants.VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
            case ErrorCodeConstants.AUTH_FAILED, ErrorCodeConstants.AUTH_REQUIRED -> HttpStatus.UNAUTHORIZED;
            case ErrorCodeConstants.FORBIDDEN -> HttpStatus.FORBIDDEN;
            case ErrorCodeConstants.NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ErrorCodeConstants.METHOD_NOT_ALLOWED -> HttpStatus.METHOD_NOT_ALLOWED;
            case ErrorCodeConstants.CONFLICT -> HttpStatus.CONFLICT;
            case ErrorCodeConstants.UNSUPPORTED_MEDIA_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case ErrorCodeConstants.RATE_LIMIT_EXCEEDED -> HttpStatus.TOO_MANY_REQUESTS;
            case ErrorCodeConstants.SERVICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
    
    /**
     * 메시지 코드에 따른 메시지 텍스트 반환
     */
    private String getMessageText(String messageCode) {
        return switch (messageCode) {
            case ErrorCodeConstants.MESSAGE_CODE_SUCCESS -> "성공";
            case ErrorCodeConstants.MESSAGE_CODE_BAD_REQUEST -> "잘못된 요청입니다.";
            case ErrorCodeConstants.MESSAGE_CODE_AUTH_FAILED -> "인증에 실패했습니다.";
            case ErrorCodeConstants.MESSAGE_CODE_AUTH_REQUIRED -> "인증이 필요합니다.";
            case ErrorCodeConstants.MESSAGE_CODE_FORBIDDEN -> "권한이 없습니다.";
            case ErrorCodeConstants.MESSAGE_CODE_NOT_FOUND -> "리소스를 찾을 수 없습니다.";
            case ErrorCodeConstants.MESSAGE_CODE_METHOD_NOT_ALLOWED -> "허용되지 않는 HTTP 메서드입니다.";
            case ErrorCodeConstants.MESSAGE_CODE_CONFLICT -> "충돌이 발생했습니다.";
            case ErrorCodeConstants.MESSAGE_CODE_UNSUPPORTED_MEDIA_TYPE -> "지원하지 않는 Content-Type입니다.";
            case ErrorCodeConstants.MESSAGE_CODE_VALIDATION_ERROR -> "유효성 검증에 실패했습니다.";
            case ErrorCodeConstants.MESSAGE_CODE_RATE_LIMIT_EXCEEDED -> "요청 한도를 초과했습니다.";
            case ErrorCodeConstants.MESSAGE_CODE_INTERNAL_SERVER_ERROR -> "내부 서버 오류가 발생했습니다.";
            case ErrorCodeConstants.MESSAGE_CODE_DATABASE_ERROR -> "데이터베이스 오류가 발생했습니다.";
            case ErrorCodeConstants.MESSAGE_CODE_EXTERNAL_API_ERROR -> "외부 API 오류가 발생했습니다.";
            case ErrorCodeConstants.MESSAGE_CODE_SERVICE_UNAVAILABLE -> "서비스를 사용할 수 없습니다.";
            case ErrorCodeConstants.MESSAGE_CODE_TIMEOUT -> "요청 처리 시간이 초과되었습니다.";
            default -> "오류가 발생했습니다.";
        };
    }
}

