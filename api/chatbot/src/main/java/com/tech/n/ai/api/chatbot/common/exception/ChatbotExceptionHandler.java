package com.tech.n.ai.api.chatbot.common.exception;

import com.tech.n.ai.common.conversation.exception.ConversationSessionNotFoundException;
import com.tech.n.ai.common.core.constants.ErrorCodeConstants;
import com.tech.n.ai.common.core.dto.ApiResponse;
import com.tech.n.ai.common.core.dto.MessageCode;
import com.tech.n.ai.common.exception.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class ChatbotExceptionHandler {
    
    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidInput(InvalidInputException e) {
        log.warn("Invalid input: {}", e.getMessage());
        return ResponseEntity.badRequest()
            .body(createErrorResponse(ErrorCodeConstants.VALIDATION_ERROR, 
                ErrorCodeConstants.MESSAGE_CODE_VALIDATION_ERROR, e.getMessage()));
    }
    
    @ExceptionHandler(TokenLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleTokenLimitExceeded(TokenLimitExceededException e) {
        log.warn("Token limit exceeded: {}", e.getMessage());
        return ResponseEntity.badRequest()
            .body(createErrorResponse(ErrorCodeConstants.VALIDATION_ERROR, 
                ErrorCodeConstants.MESSAGE_CODE_VALIDATION_ERROR, e.getMessage()));
    }
    
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException e) {
        log.warn("Unauthorized access: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(createErrorResponse(ErrorCodeConstants.FORBIDDEN, 
                ErrorCodeConstants.MESSAGE_CODE_FORBIDDEN, e.getMessage()));
    }
    
    @ExceptionHandler(ConversationSessionNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleSessionNotFound(ConversationSessionNotFoundException e) {
        log.warn("Session not found: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(createErrorResponse(ErrorCodeConstants.NOT_FOUND,
                ErrorCodeConstants.MESSAGE_CODE_NOT_FOUND, e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(MethodArgumentNotValidException e) {
        Map<String, String> errors = e.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                fieldError -> fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "유효성 검증 실패",
                (existing, replacement) -> existing
            ));

        log.warn("Validation failed: {}", errors);

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

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(NoResourceFoundException e) {
        log.warn("Resource not found: {} {}", e.getHttpMethod(), e.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(createErrorResponse(ErrorCodeConstants.NOT_FOUND,
                ErrorCodeConstants.MESSAGE_CODE_NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception e) {
        log.error("Unexpected error in chatbot", e);
        return ResponseEntity.internalServerError()
            .body(createErrorResponse(ErrorCodeConstants.INTERNAL_SERVER_ERROR,
                ErrorCodeConstants.MESSAGE_CODE_INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."));
    }
    
    private ApiResponse<Void> createErrorResponse(String errorCode, String messageCode, String message) {
        return ApiResponse.error(errorCode, new MessageCode(messageCode, message));
    }
}
