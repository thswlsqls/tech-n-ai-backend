package com.tech.n.ai.domain.mariadb.entity.auth;

import com.tech.n.ai.domain.mariadb.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "admins")
@Getter
@Setter
public class AdminEntity extends BaseEntity {

    @Column(name = "email", length = 100, nullable = false, unique = true)
    private String email;

    @Column(name = "username", length = 50, nullable = false, unique = true)
    private String username;

    @Column(name = "password", length = 255, nullable = false)
    private String password;

    @Column(name = "role", length = 50, nullable = false)
    private String role;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "last_login_at", precision = 6)
    private LocalDateTime lastLoginAt;

    @Column(name = "failed_login_attempts", nullable = false)
    private Integer failedLoginAttempts = 0;

    @Column(name = "account_locked_until", precision = 6)
    private LocalDateTime accountLockedUntil;

    public boolean isAccountLocked() {
        return accountLockedUntil != null && accountLockedUntil.isAfter(LocalDateTime.now());
    }

    public void recordLoginFailure() {
        this.failedLoginAttempts = (this.failedLoginAttempts == null ? 0 : this.failedLoginAttempts) + 1;
    }

    public void resetLoginFailures() {
        this.failedLoginAttempts = 0;
        this.accountLockedUntil = null;
    }
}
