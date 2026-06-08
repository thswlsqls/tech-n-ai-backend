package com.tech.n.ai.common.security.handler;

import com.tech.n.ai.common.core.constants.ErrorCodeConstants;
import com.tech.n.ai.common.security.support.SecurityErrorResponseWriter;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Spring Security 접근 거부 핸들러
 * 인증된 사용자가 권한 없는 리소스에 접근할 때 403 응답 반환
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(
        HttpServletRequest request,
        HttpServletResponse response,
        AccessDeniedException accessDeniedException
    ) throws IOException, ServletException {
        log.warn("접근 권한 없는 요청: {} {}", request.getMethod(), request.getRequestURI());

        SecurityErrorResponseWriter.writeError(
            objectMapper, response,
            HttpServletResponse.SC_FORBIDDEN,
            ErrorCodeConstants.FORBIDDEN,
            ErrorCodeConstants.MESSAGE_CODE_FORBIDDEN,
            "권한이 없습니다."
        );
    }
}
