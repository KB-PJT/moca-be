package com.moca.mocabe.global.auth;

/** 실행 환경별 refresh token cookie 보안 속성을 관리한다. */
public class RefreshCookiePolicy {

    private final boolean secure;

    public RefreshCookiePolicy(boolean secure) {
        this.secure = secure;
    }

    public boolean isSecure() {
        return secure;
    }
}
