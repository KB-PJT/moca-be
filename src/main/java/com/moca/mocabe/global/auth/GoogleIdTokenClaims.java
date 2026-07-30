package com.moca.mocabe.global.auth;

/** Google ID Token 검증 후 인증 유스케이스에 전달하는 최소 사용자 정보다. */
public class GoogleIdTokenClaims {

    private final String subject;
    private final String email;
    private final String name;

    public GoogleIdTokenClaims(String subject, String email, String name) {
        this.subject = subject;
        this.email = email;
        this.name = name;
    }

    public String getSubject() {
        return subject;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }
}
