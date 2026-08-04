package com.moca.mocabe.global.auth;

/** Redis 세션에서 복원한 인증 사용자 정보다. */
public class AuthenticatedUser {

    private final String userId;
    private final String userType;

    public AuthenticatedUser(String userId, String userType) {
        this.userId = userId;
        this.userType = userType;
    }

    public String getUserId() {
        return userId;
    }

    public String getUserType() {
        return userType;
    }
}
