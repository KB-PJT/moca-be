package com.moca.mocabe.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.moca.mocabe.global.auth.OpaqueTokenPair;

/** refresh cookie 회전 후 반환하는 새 access token이다. */
public class RefreshTokenResponse {

    private final String accessToken;
    private final String refreshToken;
    private final long accessTokenExpiresIn;

    public RefreshTokenResponse(OpaqueTokenPair tokens) {
        this.accessToken = tokens.getAccessToken();
        this.refreshToken = tokens.getRefreshToken();
        this.accessTokenExpiresIn = tokens.getAccessTokenExpiresIn();
    }

    public String getAccessToken() {
        return accessToken;
    }

    public long getAccessTokenExpiresIn() {
        return accessTokenExpiresIn;
    }

    @JsonIgnore
    public String getRefreshToken() {
        return refreshToken;
    }
}
