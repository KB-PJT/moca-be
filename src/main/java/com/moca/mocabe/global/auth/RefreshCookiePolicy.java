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

    /** HTTPS 배포에서는 cross-site refresh 요청을 위해 None을 사용한다. */
    public String getSameSite() {
        return secure ? "None" : "Lax";
    }
}
