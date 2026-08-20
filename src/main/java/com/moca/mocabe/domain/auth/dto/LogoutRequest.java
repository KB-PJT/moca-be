package com.moca.mocabe.domain.auth.dto;

import javax.validation.constraints.Size;

/** 로그아웃 시 함께 해제할 FCM 디바이스 토큰을 선택적으로 전달하는 요청이다. */
public class LogoutRequest {

    @Size(max = 2048)
    private String fcmToken;

    public String getFcmToken() {
        return fcmToken;
    }

    public void setFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

}
