package com.tech.n.ai.api.auth.controller;

import com.tech.n.ai.api.auth.dto.LoginRequest;
import com.tech.n.ai.api.auth.dto.TokenResponse;
import com.tech.n.ai.api.auth.dto.admin.AdminCreateRequest;
import com.tech.n.ai.api.auth.dto.admin.AdminResponse;
import com.tech.n.ai.api.auth.dto.admin.AdminUpdateRequest;
import com.tech.n.ai.api.auth.facade.AdminFacade;
import com.tech.n.ai.common.security.principal.UserPrincipal;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AdminController 슬라이스 테스트
 *
 * Security 비활성화하여 순수 Controller 로직만 테스트.
 * 인증/인가 테스트는 별도 통합테스트에서 수행.
 */
@WebMvcTest(
    controllers = AdminController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "com\\.tech\\.n\\.ai\\.common\\.security\\..*"
    )
)
@Import(AdminControllerTest.TestSecurityConfig.class)
@DisplayName("AdminController 슬라이스 테스트")
class AdminControllerTest {

    @TestConfiguration
    static class TestSecurityConfig implements WebMvcConfigurer {
        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new AuthenticationPrincipalArgumentResolver());
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminFacade adminFacade;

    // ========== POST /admin/login ==========

    @Nested
    @DisplayName("POST /api/v1/auth/admin/login")
    class AdminLogin {

        @Test
        @DisplayName("정상 로그인 - 200 OK")
        void login_성공() throws Exception {
            LoginRequest request = new LoginRequest("admin@example.com", "Password1!");
            TokenResponse tokenResponse = new TokenResponse("access-token", "refresh-token", "Bearer", 3600L, 604800L);
            when(adminFacade.login(any(LoginRequest.class))).thenReturn(tokenResponse);

            mockMvc.perform(post("/api/v1/auth/admin/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("2000"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
        }

        @Test
        @DisplayName("이메일 누락 - 400 Bad Request")
        void login_이메일_누락() throws Exception {
            String body = """
                {"password": "Password1!"}
                """;

            mockMvc.perform(post("/api/v1/auth/admin/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("비밀번호 누락 - 400 Bad Request")
        void login_비밀번호_누락() throws Exception {
            String body = """
                {"email": "admin@example.com"}
                """;

            mockMvc.perform(post("/api/v1/auth/admin/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("잘못된 이메일 형식 - 400 Bad Request")
        void login_잘못된_이메일() throws Exception {
            LoginRequest request = new LoginRequest("not-an-email", "Password1!");

            mockMvc.perform(post("/api/v1/auth/admin/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("빈 요청 본문 - 400 Bad Request")
        void login_빈_본문() throws Exception {
            mockMvc.perform(post("/api/v1/auth/admin/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isBadRequest());
        }
    }

    // ========== POST /admin/accounts ==========

    @Nested
    @DisplayName("POST /api/v1/auth/admin/accounts")
    class CreateAdmin {

        @Test
        @DisplayName("정상 관리자 생성 - 200 OK")
        void createAdmin_성공() throws Exception {
            AdminCreateRequest request = new AdminCreateRequest("new@example.com", "newadmin", "Password1!");
            AdminResponse response = new AdminResponse(1L, "new@example.com", "newadmin", "ADMIN", true, LocalDateTime.now(), null);
            when(adminFacade.createAdmin(any(AdminCreateRequest.class), anyLong())).thenReturn(response);

            UserPrincipal principal = new UserPrincipal(10L, "creator@example.com", "ADMIN");
            SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())
            );

            mockMvc.perform(post("/api/v1/auth/admin/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("2000"))
                .andExpect(jsonPath("$.data.email").value("new@example.com"))
                .andExpect(jsonPath("$.data.username").value("newadmin"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));

            SecurityContextHolder.clearContext();
        }

        @Test
        @DisplayName("이메일 누락 - 400 Bad Request")
        void createAdmin_이메일_누락() throws Exception {
            String body = """
                {"username": "newadmin", "password": "Password1!"}
                """;

            mockMvc.perform(post("/api/v1/auth/admin/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("잘못된 이메일 형식 - 400 Bad Request")
        void createAdmin_잘못된_이메일() throws Exception {
            AdminCreateRequest request = new AdminCreateRequest("not-an-email", "newadmin", "Password1!");

            mockMvc.perform(post("/api/v1/auth/admin/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("사용자명 누락 - 400 Bad Request")
        void createAdmin_사용자명_누락() throws Exception {
            String body = """
                {"email": "new@example.com", "password": "Password1!"}
                """;

            mockMvc.perform(post("/api/v1/auth/admin/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("비밀번호 8자 미만 - 400 Bad Request")
        void createAdmin_짧은_비밀번호() throws Exception {
            AdminCreateRequest request = new AdminCreateRequest("new@example.com", "newadmin", "Pass1!");

            mockMvc.perform(post("/api/v1/auth/admin/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("사용자명 1자 (2자 미만) - 400 Bad Request")
        void createAdmin_짧은_사용자명() throws Exception {
            AdminCreateRequest request = new AdminCreateRequest("new@example.com", "a", "Password1!");

            mockMvc.perform(post("/api/v1/auth/admin/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }
    }

    // ========== GET /admin/accounts ==========

    @Nested
    @DisplayName("GET /api/v1/auth/admin/accounts")
    class ListAdmins {

        @Test
        @DisplayName("관리자 목록 조회 - 200 OK")
        void listAdmins_성공() throws Exception {
            List<AdminResponse> admins = List.of(
                new AdminResponse(1L, "a1@example.com", "admin1", "ADMIN", true, LocalDateTime.now(), null),
                new AdminResponse(2L, "a2@example.com", "admin2", "ADMIN", true, LocalDateTime.now(), null)
            );
            when(adminFacade.listAdmins()).thenReturn(admins);

            mockMvc.perform(get("/api/v1/auth/admin/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("2000"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].email").value("a1@example.com"));
        }
    }

    // ========== GET /admin/accounts/{adminId} ==========

    @Nested
    @DisplayName("GET /api/v1/auth/admin/accounts/{adminId}")
    class GetAdmin {

        @Test
        @DisplayName("관리자 상세 조회 - 200 OK")
        void getAdmin_성공() throws Exception {
            AdminResponse response = new AdminResponse(1L, "admin@example.com", "admin1", "ADMIN", true, LocalDateTime.now(), null);
            when(adminFacade.getAdmin(1L)).thenReturn(response);

            mockMvc.perform(get("/api/v1/auth/admin/accounts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("2000"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.email").value("admin@example.com"));
        }
    }

    // ========== PUT /admin/accounts/{adminId} ==========

    @Nested
    @DisplayName("PUT /api/v1/auth/admin/accounts/{adminId}")
    class UpdateAdmin {

        @Test
        @DisplayName("정상 관리자 수정 - 200 OK")
        void updateAdmin_성공() throws Exception {
            AdminUpdateRequest request = new AdminUpdateRequest("newname", "NewPassword1!");
            AdminResponse response = new AdminResponse(1L, "admin@example.com", "newname", "ADMIN", true, LocalDateTime.now(), null);
            when(adminFacade.updateAdmin(eq(1L), any(AdminUpdateRequest.class), anyLong())).thenReturn(response);

            UserPrincipal principal = new UserPrincipal(10L, "admin@example.com", "ADMIN");
            SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())
            );

            mockMvc.perform(put("/api/v1/auth/admin/accounts/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("2000"))
                .andExpect(jsonPath("$.data.username").value("newname"));

            SecurityContextHolder.clearContext();
        }

        @Test
        @DisplayName("사용자명 1자 (2자 미만) - 400 Bad Request")
        void updateAdmin_짧은_사용자명() throws Exception {
            AdminUpdateRequest request = new AdminUpdateRequest("a", null);

            mockMvc.perform(put("/api/v1/auth/admin/accounts/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("비밀번호 8자 미만 - 400 Bad Request")
        void updateAdmin_짧은_비밀번호() throws Exception {
            AdminUpdateRequest request = new AdminUpdateRequest(null, "Pass1!");

            mockMvc.perform(put("/api/v1/auth/admin/accounts/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("모든 필드 null - 200 OK (변경 없음)")
        void updateAdmin_빈_요청() throws Exception {
            AdminResponse response = new AdminResponse(1L, "admin@example.com", "admin1", "ADMIN", true, LocalDateTime.now(), null);
            when(adminFacade.updateAdmin(eq(1L), any(AdminUpdateRequest.class), anyLong())).thenReturn(response);

            UserPrincipal principal = new UserPrincipal(10L, "admin@example.com", "ADMIN");
            SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())
            );

            mockMvc.perform(put("/api/v1/auth/admin/accounts/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isOk());

            SecurityContextHolder.clearContext();
        }
    }

    // ========== DELETE /admin/accounts/{adminId} ==========

    @Nested
    @DisplayName("DELETE /api/v1/auth/admin/accounts/{adminId}")
    class DeleteAdmin {

        @Test
        @DisplayName("정상 관리자 삭제 - 200 OK")
        void deleteAdmin_성공() throws Exception {
            UserPrincipal principal = new UserPrincipal(2L, "admin@example.com", "ADMIN");
            SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())
            );

            try {
                mockMvc.perform(delete("/api/v1/auth/admin/accounts/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("2000"));

                verify(adminFacade).deleteAdmin(1L, 2L);
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }
}
