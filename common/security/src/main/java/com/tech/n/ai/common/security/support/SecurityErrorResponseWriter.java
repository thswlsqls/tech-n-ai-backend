package com.tech.n.ai.common.security.support;

import com.tech.n.ai.common.core.dto.ApiResponse;
import com.tech.n.ai.common.core.dto.MessageCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Spring Security 단계에서 인증·인가 실패를 JSON 에러 응답으로 직접 내려보낼 때 쓰는 유틸.
 *
 * 필터와 핸들러들이 서로 다른 Spring 타입을 구현/상속해 공통 베이스가 없으므로,
 * 응답 작성 로직을 이 정적 메서드 한 곳에 모은다.
 */
public final class SecurityErrorResponseWriter {

    private SecurityErrorResponseWriter() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static void writeError(
        ObjectMapper objectMapper,
        HttpServletResponse response,
        int status,
        String errorCode,
        String messageCode,
        String text
    ) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        var errorResponse = ApiResponse.error(errorCode, new MessageCode(messageCode, text));
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}
