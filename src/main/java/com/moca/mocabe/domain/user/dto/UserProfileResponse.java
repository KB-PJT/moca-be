package com.moca.mocabe.domain.user.dto;

import com.moca.mocabe.domain.user.model.UserProfile;

/** 마이페이지 프로필 응답이다. */
public class UserProfileResponse {

    private final String userId;
    private final String nickname;
    private final String email;
    private final String userType;
    private final String cardSortMode;

    public UserProfileResponse(UserProfile profile) {
        this.userId = profile.getUserId();
        this.nickname = profile.getNickname();
        this.email = profile.getEmail();
        this.userType = profile.getUserType();
        this.cardSortMode = profile.getCardSortMode();
    }

    public String getUserId() {
        return userId;
    }

    public String getNickname() {
        return nickname;
    }

    public String getEmail() {
        return email;
    }

    public String getUserType() {
        return userType;
    }

    public String getCardSortMode() {
        return cardSortMode;
    }
}
