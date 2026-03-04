package com.tech.n.ai.api.auth.service;

import com.tech.n.ai.api.auth.dto.LoginRequest;
import com.tech.n.ai.api.auth.dto.TokenResponse;
import com.tech.n.ai.api.auth.dto.admin.AdminCreateRequest;
import com.tech.n.ai.api.auth.dto.admin.AdminResponse;
import com.tech.n.ai.api.auth.dto.admin.AdminUpdateRequest;
import com.tech.n.ai.common.exception.exception.ConflictException;
import com.tech.n.ai.common.exception.exception.ForbiddenException;
import com.tech.n.ai.common.exception.exception.ResourceNotFoundException;
import com.tech.n.ai.common.exception.exception.UnauthorizedException;
import com.tech.n.ai.domain.mariadb.entity.auth.AdminEntity;
import com.tech.n.ai.domain.mariadb.repository.reader.auth.AdminReaderRepository;
import com.tech.n.ai.domain.mariadb.repository.writer.auth.AdminWriterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private static final String ADMIN_ROLE = "ADMIN";

    private final AdminReaderRepository adminReaderRepository;
    private final AdminWriterRepository adminWriterRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

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
        admin.setIsActive(false);
        admin.setDeletedBy(currentAdminId);
        adminWriterRepository.delete(admin);

        log.info("Admin deleted: adminId={}, deletedBy={}", adminId, currentAdminId);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        AdminEntity admin = adminReaderRepository.findByEmailAndIsActiveTrueAndIsDeletedFalse(request.email())
            .orElseThrow(() -> new UnauthorizedException("이메일 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(request.password(), admin.getPassword())) {
            throw new UnauthorizedException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        admin.setLastLoginAt(LocalDateTime.now());
        adminWriterRepository.save(admin);

        return tokenService.generateTokens(admin.getId(), admin.getEmail(), admin.getRole());
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
