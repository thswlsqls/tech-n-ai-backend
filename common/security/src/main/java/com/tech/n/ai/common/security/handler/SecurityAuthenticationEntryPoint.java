package com.tech.n.ai.common.security.handler;

import com.tech.n.ai.common.core.constants.ErrorCodeConstants;
import com.tech.n.ai.common.security.support.SecurityErrorResponseWriter;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Spring Security 인증 실패 진입점
 * 인증되지 않은 사용자가 보호된 리소스에 접근할 때 401 응답 반환
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException authException
    ) throws IOException, ServletException {
        log.warn("인증되지 않은 접근 시도: {} {}", request.getMethod(), request.getRequestURI());

        SecurityErrorResponseWriter.writeError(
            objectMapper, response,
            HttpServletResponse.SC_UNAUTHORIZED,
            ErrorCodeConstants.AUTH_REQUIRED,
            ErrorCodeConstants.MESSAGE_CODE_AUTH_REQUIRED,
            "인증이 필요합니다."
        );
    }
}
