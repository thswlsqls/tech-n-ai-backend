package com.tech.n.ai.api.auth.dto.admin;

import com.tech.n.ai.domain.aurora.entity.auth.AdminEntity;

import java.time.LocalDateTime;

public record AdminResponse(
    Long id,
    String email,
    String username,
    String role,
    Boolean isActive,
    LocalDateTime createdAt,
    LocalDateTime lastLoginAt
) {
    public static AdminResponse from(AdminEntity entity) {
        return new AdminResponse(
            entity.getId(),
            entity.getEmail(),
            entity.getUsername(),
            entity.getRole(),
            entity.getIsActive(),
            entity.getCreatedAt(),
            entity.getLastLoginAt()
        );
    }
}
