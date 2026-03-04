package com.tech.n.ai.api.auth.facade;

import com.tech.n.ai.api.auth.dto.LoginRequest;
import com.tech.n.ai.api.auth.dto.TokenResponse;
import com.tech.n.ai.api.auth.dto.admin.AdminCreateRequest;
import com.tech.n.ai.api.auth.dto.admin.AdminResponse;
import com.tech.n.ai.api.auth.dto.admin.AdminUpdateRequest;
import com.tech.n.ai.api.auth.service.AdminService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminFacade 단위 테스트")
class AdminFacadeTest {

    @Mock
    private AdminService adminService;

    @InjectMocks
    private AdminFacade adminFacade;

    @Nested
    @DisplayName("createAdmin")
    class CreateAdmin {

        @Test
        @DisplayName("AdminService에 위임")
        void createAdmin_위임() {
            // Given
            AdminCreateRequest request = new AdminCreateRequest("admin@example.com", "admin", "Password1!");
            AdminResponse expected = new AdminResponse(1L, "admin@example.com", "admin", "ADMIN", true, LocalDateTime.now(), null);
            when(adminService.createAdmin(request, 100L)).thenReturn(expected);

            // When
            AdminResponse result = adminFacade.createAdmin(request, 100L);

            // Then
            assertThat(result).isEqualTo(expected);
            verify(adminService).createAdmin(request, 100L);
        }
    }

    @Nested
    @DisplayName("listAdmins")
    class ListAdmins {

        @Test
        @DisplayName("AdminService에 위임")
        void listAdmins_위임() {
            // Given
            List<AdminResponse> expected = List.of(
                new AdminResponse(1L, "a@example.com", "admin1", "ADMIN", true, LocalDateTime.now(), null)
            );
            when(adminService.listAdmins()).thenReturn(expected);

            // When
            List<AdminResponse> result = adminFacade.listAdmins();

            // Then
            assertThat(result).isEqualTo(expected);
            verify(adminService).listAdmins();
        }
    }

    @Nested
    @DisplayName("getAdmin")
    class GetAdmin {

        @Test
        @DisplayName("AdminService에 위임")
        void getAdmin_위임() {
            // Given
            AdminResponse expected = new AdminResponse(1L, "admin@example.com", "admin", "ADMIN", true, LocalDateTime.now(), null);
            when(adminService.getAdmin(1L)).thenReturn(expected);

            // When
            AdminResponse result = adminFacade.getAdmin(1L);

            // Then
            assertThat(result).isEqualTo(expected);
            verify(adminService).getAdmin(1L);
        }
    }

    @Nested
    @DisplayName("updateAdmin")
    class UpdateAdmin {

        @Test
        @DisplayName("AdminService에 위임")
        void updateAdmin_위임() {
            // Given
            AdminUpdateRequest request = new AdminUpdateRequest("newname", null);
            AdminResponse expected = new AdminResponse(1L, "admin@example.com", "newname", "ADMIN", true, LocalDateTime.now(), null);
            when(adminService.updateAdmin(1L, request, 100L)).thenReturn(expected);

            // When
            AdminResponse result = adminFacade.updateAdmin(1L, request, 100L);

            // Then
            assertThat(result).isEqualTo(expected);
            verify(adminService).updateAdmin(1L, request, 100L);
        }
    }

    @Nested
    @DisplayName("deleteAdmin")
    class DeleteAdmin {

        @Test
        @DisplayName("AdminService에 위임")
        void deleteAdmin_위임() {
            // When
            adminFacade.deleteAdmin(1L, 2L);

            // Then
            verify(adminService).deleteAdmin(1L, 2L);
        }
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("AdminService에 위임")
        void login_위임() {
            // Given
            LoginRequest request = new LoginRequest("admin@example.com", "Password1!");
            TokenResponse expected = new TokenResponse("access", "refresh", "Bearer", 3600L, 604800L);
            when(adminService.login(request)).thenReturn(expected);

            // When
            TokenResponse result = adminFacade.login(request);

            // Then
            assertThat(result).isEqualTo(expected);
            verify(adminService).login(request);
        }
    }
}
