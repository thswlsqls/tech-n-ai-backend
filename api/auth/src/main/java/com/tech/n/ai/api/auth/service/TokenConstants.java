package com.tech.n.ai.api.auth.service;

public final class TokenConstants {
    
    public static final String USER_ROLE = "USER";
    public static final String TOKEN_TYPE = "Bearer";
    public static final long ACCESS_TOKEN_EXPIRY_SECONDS = 3600L;
    public static final long REFRESH_TOKEN_EXPIRY_SECONDS = 604800L;
    public static final long ADMIN_ACCESS_TOKEN_EXPIRY_SECONDS = 900L;
    public static final long ADMIN_REFRESH_TOKEN_EXPIRY_SECONDS = 86400L;
    public static final int TOKEN_BYTE_SIZE = 32;
    
    private TokenConstants() {
        throw new AssertionError("Cannot instantiate constants class");
    }
}
