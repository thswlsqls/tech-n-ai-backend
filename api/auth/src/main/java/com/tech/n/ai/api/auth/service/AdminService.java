package com.tech.n.ai.api.auth.service;

import com.tech.n.ai.api.auth.dto.LoginRequest;
import com.tech.n.ai.api.auth.dto.RefreshTokenRequest;
import com.tech.n.ai.api.auth.dto.TokenResponse;
import com.tech.n.ai.api.auth.dto.admin.AdminCreateRequest;
import com.tech.n.ai.api.auth.dto.admin.AdminResponse;
import com.tech.n.ai.api.auth.dto.admin.AdminUpdateRequest;
import com.tech.n.ai.common.exception.exception.ConflictException;
import com.tech.n.ai.common.exception.exception.ForbiddenException;
import com.tech.n.ai.common.exception.exception.ResourceNotFoundException;
import com.tech.n.ai.common.exception.exception.UnauthorizedException;
import com.tech.n.ai.common.security.jwt.JwtTokenPayload;
import com.tech.n.ai.domain.aurora.entity.auth.AdminEntity;
import com.tech.n.ai.domain.aurora.entity.auth.RefreshTokenEntity;
import com.tech.n.ai.domain.aurora.repository.reader.auth.AdminReaderRepository;
import com.tech.n.ai.domain.aurora.repository.writer.auth.AdminWriterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private static final String ADMIN_ROLE = "ADMIN";
    private static final int LOCK_THRESHOLD_FIRST = 5;
    private static final int LOCK_THRESHOLD_SECOND = 10;
    private static final Duration LOCK_DURATION_FIRST = Duration.ofMinutes(15);
    private static final Duration LOCK_DURATION_SECOND = Duration.ofHours(1);

    private final AdminReaderRepository adminReaderRepository;
    private final AdminWriterRepository adminWriterRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public AdminResponse createAdmin(AdminCreateRequest request, Long currentAdminId) {
        adminReaderRepository.findByEmail(request.email())
            .ifPresent(a -> { throw new ConflictException("email", "이미 등록된 이메일입니다."); });

        adminReaderRepository.findByUsername(request.username())
            .ifPresent(a -> { throw new ConflictException("username", "이미 등록된 사용자명입니다."); });

        AdminEntity admin = new AdminEntity();
        admin.setEmail(request.email());
        admin.setUsername(request.username());
        admin.setPassword(passwordEncoder.encode(request.password()));
        admin.setRole(ADMIN_ROLE);
        admin.setIsActive(true);
        admin.setCreatedBy(currentAdminId);

        adminWriterRepository.save(admin);
        log.info("Admin created: email={}", request.email());
        return AdminResponse.from(admin);
    }

    @Transactional(readOnly = true)
    public List<AdminResponse> listAdmins() {
        return adminReaderRepository.findByIsActiveTrueAndIsDeletedFalse().stream()
            .map(AdminResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public AdminResponse getAdmin(Long adminId) {
        AdminEntity admin = findActiveAdmin(adminId);
        return AdminResponse.from(admin);
    }

    @Transactional
    public AdminResponse updateAdmin(Long adminId, AdminUpdateRequest request, Long currentAdminId) {
        AdminEntity admin = findActiveAdmin(adminId);

        if (request.username() != null && !request.username().isBlank()
                && !admin.getUsername().equals(request.username())) {
            adminReaderRepository.findByUsername(request.username())
                .ifPresent(a -> { throw new ConflictException("username", "이미 등록된 사용자명입니다."); });
            admin.setUsername(request.username());
        }

        if (request.password() != null && !request.password().isBlank()) {
            admin.setPassword(passwordEncoder.encode(request.password()));
        }

        admin.setUpdatedBy(currentAdminId);
        adminWriterRepository.save(admin);
        log.info("Admin updated: adminId={}", adminId);
        return AdminResponse.from(admin);
    }

    @Transactional
    public void deleteAdmin(Long adminId, Long currentAdminId) {
        if (adminId.equals(currentAdminId)) {
            throw new ForbiddenException("자기 자신은 삭제할 수 없습니다.");
        }

        AdminEntity admin = findActiveAdmin(adminId);

        refreshTokenService.deleteAllAdminRefreshTokens(adminId);

        admin.setIsActive(false);
        admin.setDeletedBy(currentAdminId);
        adminWriterRepository.delete(admin);

        log.info("Admin deleted: adminId={}, deletedBy={}", adminId, currentAdminId);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        AdminEntity admin = adminReaderRepository.findByEmailAndIsActiveTrueAndIsDeletedFalse(request.email())
            .orElseThrow(() -> new UnauthorizedException("이메일 또는 비밀번호가 올바르지 않습니다."));

        if (admin.isAccountLocked()) {
            throw new UnauthorizedException("계정이 잠겨 있습니다. 잠시 후 다시 시도해주세요.");
        }

        if (!passwordEncoder.matches(request.password(), admin.getPassword())) {
            handleLoginFailure(admin);
            throw new UnauthorizedException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        admin.resetLoginFailures();
        admin.setLastLoginAt(LocalDateTime.now());
        adminWriterRepository.save(admin);

        return tokenService.generateTokens(admin.getId(), admin.getEmail(), admin.getRole());
    }

    @Transactional
    public void logout(Long adminId, String refreshToken) {
        RefreshTokenEntity tokenEntity = findAndValidateAdminRefreshToken(refreshToken, adminId);
        refreshTokenService.deleteRefreshToken(tokenEntity);
        log.info("Admin logout: adminId={}", adminId);
    }

    @Transactional
    public TokenResponse refreshToken(RefreshTokenRequest request) {
        if (!tokenService.validateToken(request.refreshToken())) {
            throw new UnauthorizedException("유효하지 않은 Refresh Token입니다.");
        }

        JwtTokenPayload payload = tokenService.getPayloadFromToken(request.refreshToken());

        if (!ADMIN_ROLE.equals(payload.role())) {
            throw new UnauthorizedException("관리자 전용 토큰 갱신 경로입니다.");
        }

        RefreshTokenEntity tokenEntity = refreshTokenService.findRefreshToken(request.refreshToken())
            .orElseThrow(() -> new UnauthorizedException("유효하지 않은 Refresh Token입니다."));

        Long adminId = Long.parseLong(payload.userId());

        if (tokenEntity.getAdminId() == null || !tokenEntity.getAdminId().equals(adminId)) {
            throw new UnauthorizedException("Refresh Token의 관리자 ID가 일치하지 않습니다.");
        }

        validateTokenExpiry(tokenEntity);
        validateAdminActive(adminId);

        refreshTokenService.deleteRefreshToken(tokenEntity);

        return tokenService.generateTokens(adminId, payload.email(), payload.role());
    }

    private void handleLoginFailure(AdminEntity admin) {
        admin.recordLoginFailure();

        int attempts = admin.getFailedLoginAttempts();
        if (attempts >= LOCK_THRESHOLD_SECOND) {
            admin.setAccountLockedUntil(LocalDateTime.now().plus(LOCK_DURATION_SECOND));
            log.warn("Admin account locked for 1 hour: email={}, attempts={}", admin.getEmail(), attempts);
        } else if (attempts >= LOCK_THRESHOLD_FIRST) {
            admin.setAccountLockedUntil(LocalDateTime.now().plus(LOCK_DURATION_FIRST));
            log.warn("Admin account locked for 15 minutes: email={}, attempts={}", admin.getEmail(), attempts);
        }

        adminWriterRepository.save(admin);
    }

    private RefreshTokenEntity findAndValidateAdminRefreshToken(String refreshToken, Long adminId) {
        RefreshTokenEntity entity = refreshTokenService.findRefreshToken(refreshToken)
            .orElseThrow(() -> new UnauthorizedException("유효하지 않은 Refresh Token입니다."));

        if (entity.getAdminId() == null || !entity.getAdminId().equals(adminId)) {
            throw new UnauthorizedException("Refresh Token의 관리자 ID가 일치하지 않습니다.");
        }

        return entity;
    }

    private void validateTokenExpiry(RefreshTokenEntity entity) {
        if (entity.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException("만료된 Refresh Token입니다.");
        }
    }

    private void validateAdminActive(Long adminId) {
        AdminEntity admin = adminReaderRepository.findById(adminId).orElse(null);
        if (admin == null || Boolean.TRUE.equals(admin.getIsDeleted()) || !Boolean.TRUE.equals(admin.getIsActive())) {
            throw new UnauthorizedException("비활성화된 관리자 계정입니다.");
        }
    }

    private AdminEntity findActiveAdmin(Long adminId) {
        AdminEntity admin = adminReaderRepository.findById(adminId)
            .orElseThrow(() -> new ResourceNotFoundException("관리자를 찾을 수 없습니다."));

        if (Boolean.TRUE.equals(admin.getIsDeleted()) || !Boolean.TRUE.equals(admin.getIsActive())) {
            throw new ResourceNotFoundException("관리자를 찾을 수 없습니다.");
        }

        return admin;
    }
}
