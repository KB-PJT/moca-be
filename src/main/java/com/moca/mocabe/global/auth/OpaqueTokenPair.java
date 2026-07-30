package com.moca.mocabe.global.auth;

/** MOCA API 인증에만 사용하는 opaque access/refresh token 쌍이다. */
public class OpaqueTokenPair {

    private final String accessToken;
    private final String refreshToken;
    private final long accessTokenExpiresIn;

    public OpaqueTokenPair(String accessToken, String refreshToken, long accessTokenExpiresIn) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.accessTokenExpiresIn = accessTokenExpiresIn;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public long getAccessTokenExpiresIn() {
        return accessTokenExpiresIn;
    }
}
