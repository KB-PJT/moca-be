package com.moca.mocabe.global.auth;

/** Google access token 검증 뒤 로그인에 사용하는 최소 사용자 식별 정보다. */
public class GoogleUserIdentity {

    private final String subject;
    private final String email;

    public GoogleUserIdentity(String subject, String email) {
        this.subject = subject;
        this.email = email;
    }

    public String getSubject() {
        return subject;
    }

    public String getEmail() {
        return email;
    }
}
