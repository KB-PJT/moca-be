package com.moca.mocabe.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.moca.mocabe.domain.user.dto.UserProfileResponse;
import com.moca.mocabe.global.auth.OpaqueTokenPair;

/** Google 로그인 후 PWA에 반환하는 MOCA access token과 사용자 정보다. */
public class GoogleLoginResponse {

    private final boolean newMember;
    private final String accessToken;
    private final String refreshToken;
    private final long accessTokenExpiresIn;
    private final UserProfileResponse member;

    public GoogleLoginResponse(boolean newMember, OpaqueTokenPair tokens, UserProfileResponse member) {
        this.newMember = newMember;
        this.accessToken = tokens.getAccessToken();
        this.refreshToken = tokens.getRefreshToken();
        this.accessTokenExpiresIn = tokens.getAccessTokenExpiresIn();
        this.member = member;
    }

    public boolean isNewMember() {
        return newMember;
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

    public UserProfileResponse getMember() {
        return member;
    }
}
