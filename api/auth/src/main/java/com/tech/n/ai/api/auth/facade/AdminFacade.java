package com.tech.n.ai.api.auth.facade;

import com.tech.n.ai.api.auth.dto.LoginRequest;
import com.tech.n.ai.api.auth.dto.RefreshTokenRequest;
import com.tech.n.ai.api.auth.dto.TokenResponse;
import com.tech.n.ai.api.auth.dto.admin.AdminCreateRequest;
import com.tech.n.ai.api.auth.dto.admin.AdminResponse;
import com.tech.n.ai.api.auth.dto.admin.AdminUpdateRequest;
import com.tech.n.ai.api.auth.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminFacade {

    private final AdminService adminService;

    public AdminResponse createAdmin(AdminCreateRequest request, Long currentAdminId) {
        return adminService.createAdmin(request, currentAdminId);
    }

    public List<AdminResponse> listAdmins() {
        return adminService.listAdmins();
    }

    public AdminResponse getAdmin(Long adminId) {
        return adminService.getAdmin(adminId);
    }

    public AdminResponse updateAdmin(Long adminId, AdminUpdateRequest request, Long currentAdminId) {
        return adminService.updateAdmin(adminId, request, currentAdminId);
    }

    public void deleteAdmin(Long adminId, Long currentAdminId) {
        adminService.deleteAdmin(adminId, currentAdminId);
    }

    public TokenResponse login(LoginRequest request) {
        return adminService.login(request);
    }

    public void logout(Long adminId, String refreshToken) {
        adminService.logout(adminId, refreshToken);
    }

    public TokenResponse refreshToken(RefreshTokenRequest request) {
        return adminService.refreshToken(request);
    }
}
