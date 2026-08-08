package com.moca.mocabe.domain.user.dto;

/** 보유 카드 존재 여부로 판단한 신규 회원 여부 응답이다. */
public class NewUserCheckResponse {

    private final boolean newUser;

    public NewUserCheckResponse(boolean newUser) {
        this.newUser = newUser;
    }

    public boolean isNewUser() {
        return newUser;
    }
}
