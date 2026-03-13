package com.tech.n.ai.domain.aurora.entity.auth;

public enum AuthenticationState {
    EMAIL_NOT_VERIFIED,
    ACTIVE,
    DELETED;
    
    public static AuthenticationState from(UserEntity user) {
        if (Boolean.TRUE.equals(user.getIsDeleted())) {
            return DELETED;
        }
        if (!Boolean.TRUE.equals(user.getIsEmailVerified())) {
            return EMAIL_NOT_VERIFIED;
        }
        return ACTIVE;
    }
}
