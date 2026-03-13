package com.tech.n.ai.domain.aurora.entity.auth;

import com.tech.n.ai.domain.aurora.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * EmailVerificationEntity
 */
@Entity
@Table(name = "email_verifications")
@Getter
@Setter
public class EmailVerificationEntity extends BaseEntity {

    @Column(name = "email", length = 100, nullable = false)
    private String email;

    @Column(name = "token", length = 255, nullable = false, unique = true)
    private String token;

    @Column(name = "type", length = 50, nullable = false)
    private String type;

    @Column(name = "expires_at", nullable = false, precision = 6)
    private LocalDateTime expiresAt;

    @Column(name = "verified_at", precision = 6)
    private LocalDateTime verifiedAt;
    
    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }
    
    public boolean isVerified() {
        return verifiedAt != null;
    }
    
    public void markAsVerified() {
        this.verifiedAt = LocalDateTime.now();
    }
    
    public void invalidate() {
        setIsDeleted(true);
        setDeletedAt(LocalDateTime.now());
    }
    
    public static EmailVerificationEntity create(String email, String token, String type, LocalDateTime expiresAt) {
        EmailVerificationEntity entity = new EmailVerificationEntity();
        entity.email = email;
        entity.token = token;
        entity.type = type;
        entity.expiresAt = expiresAt;
        return entity;
    }
}
