package com.tech.n.ai.common.security.config;

import com.tech.n.ai.common.security.filter.JwtAuthenticationFilter;
import com.tech.n.ai.common.security.handler.SecurityAccessDeniedHandler;
import com.tech.n.ai.common.security.handler.SecurityAuthenticationEntryPoint;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecurityConfigCorsTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
        .withBean(JwtAuthenticationFilter.class, () -> mock(JwtAuthenticationFilter.class))
        .withBean(SecurityAuthenticationEntryPoint.class, () -> mock(SecurityAuthenticationEntryPoint.class))
        .withBean(SecurityAccessDeniedHandler.class, () -> mock(SecurityAccessDeniedHandler.class))
        .withUserConfiguration(SecurityConfig.class);

    @Test
    void 설정이_없으면_로컬_프런트엔드_두_개만_허용한다() {
        contextRunner.run(context ->
            assertThat(allowedOrigins(context.getBean(CorsConfigurationSource.class)))
                .containsExactly("http://localhost:3000", "http://localhost:3001"));
    }

    @Test
    void 콤마로_이은_설정값을_오리진_목록으로_읽는다() {
        contextRunner
            .withPropertyValues("security.cors.allowed-origins=https://beta.example.com,https://example.com")
            .run(context ->
                assertThat(allowedOrigins(context.getBean(CorsConfigurationSource.class)))
                    .containsExactly("https://beta.example.com", "https://example.com"));
    }

    private static List<String> allowedOrigins(CorsConfigurationSource source) {
        CorsConfiguration configuration = source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/v1/auth/me"));
        return configuration.getAllowedOrigins();
    }
}
