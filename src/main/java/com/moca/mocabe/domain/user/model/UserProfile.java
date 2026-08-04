package com.moca.mocabe.domain.user.model;

/** 사용자 프로필 조회용 MyBatis 모델이다. */
public class UserProfile {

    private String userId;
    private String nickname;
    private String email;
    private String userType;
    private String cardSortMode;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    public String getCardSortMode() {
        return cardSortMode;
    }

    public void setCardSortMode(String cardSortMode) {
        this.cardSortMode = cardSortMode;
    }

}
