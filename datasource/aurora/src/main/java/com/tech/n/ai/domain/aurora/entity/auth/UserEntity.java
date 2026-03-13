package com.tech.n.ai.domain.aurora.entity.auth;

import com.tech.n.ai.domain.aurora.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
public class UserEntity extends BaseEntity {

    @Column(name = "email", length = 100, nullable = false, unique = true)
    private String email;

    @Column(name = "username", length = 50, nullable = false, unique = true)
    private String username;

    @Column(name = "password", length = 255)
    private String password;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id")
    private ProviderEntity provider;

    @Column(name = "provider_id", insertable = false, updatable = false)
    private Long providerId;

    @Column(name = "provider_user_id", length = 255)
    private String providerUserId;

    @Column(name = "is_email_verified", nullable = false)
    private Boolean isEmailVerified = false;

    @Column(name = "last_login_at", precision = 6)
    private LocalDateTime lastLoginAt;
    
    public AuthenticationState getAuthenticationState() {
        return AuthenticationState.from(this);
    }
    
    public boolean isActive() {
        return getAuthenticationState() == AuthenticationState.ACTIVE;
    }
    
    public boolean isOAuthUser() {
        return providerId != null && providerUserId != null;
    }
    
    public void verifyEmail() {
        this.isEmailVerified = true;
    }
    
    public void updateLastLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }
    
    public void updateFromOAuth(String email, String username) {
        this.email = email;
        this.username = username;
    }
    
    public static UserEntity createNewUser(String email, String username, String encodedPassword) {
        UserEntity user = new UserEntity();
        user.email = email;
        user.username = username;
        user.password = encodedPassword;
        user.isEmailVerified = false;
        return user;
    }
    
    public static UserEntity createOAuthUser(String email, String username, Long providerId, String providerUserId) {
        UserEntity user = new UserEntity();
        user.email = email;
        user.username = username;
        user.password = null;
        user.providerId = providerId;
        user.providerUserId = providerUserId;
        user.isEmailVerified = true;
        return user;
    }
}
