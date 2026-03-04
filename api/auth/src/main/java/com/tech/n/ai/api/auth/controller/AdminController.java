package com.tech.n.ai.api.auth.controller;

import com.tech.n.ai.api.auth.dto.LoginRequest;
import com.tech.n.ai.api.auth.dto.TokenResponse;
import com.tech.n.ai.api.auth.dto.admin.AdminCreateRequest;
import com.tech.n.ai.api.auth.dto.admin.AdminResponse;
import com.tech.n.ai.api.auth.dto.admin.AdminUpdateRequest;
import com.tech.n.ai.api.auth.facade.AdminFacade;
import com.tech.n.ai.common.core.dto.ApiResponse;
import com.tech.n.ai.common.security.principal.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminFacade adminFacade;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> adminLogin(
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(adminFacade.login(request)));
    }

    @PostMapping("/accounts")
    public ResponseEntity<ApiResponse<AdminResponse>> createAdmin(
            @Valid @RequestBody AdminCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(adminFacade.createAdmin(request, principal.userId())));
    }

    @GetMapping("/accounts")
    public ResponseEntity<ApiResponse<List<AdminResponse>>> listAdmins(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(adminFacade.listAdmins()));
    }

    @GetMapping("/accounts/{adminId}")
    public ResponseEntity<ApiResponse<AdminResponse>> getAdmin(
            @PathVariable Long adminId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(adminFacade.getAdmin(adminId)));
    }

    @PutMapping("/accounts/{adminId}")
    public ResponseEntity<ApiResponse<AdminResponse>> updateAdmin(
            @PathVariable Long adminId,
            @Valid @RequestBody AdminUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(adminFacade.updateAdmin(adminId, request, principal.userId())));
    }

    @DeleteMapping("/accounts/{adminId}")
    public ResponseEntity<ApiResponse<Void>> deleteAdmin(
            @PathVariable Long adminId,
            @AuthenticationPrincipal UserPrincipal principal) {
        adminFacade.deleteAdmin(adminId, principal.userId());
        return ResponseEntity.ok(ApiResponse.success());
    }
}
