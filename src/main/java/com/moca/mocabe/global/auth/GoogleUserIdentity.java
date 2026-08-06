package com.moca.mocabe.global.auth;

/** Google access token 검증 뒤 로그인에 사용하는 사용자 식별·프로필 정보다. */
public class GoogleUserIdentity {

    private final String subject;
    private final String email;
    private final String profileName;

    public GoogleUserIdentity(String subject, String email, String profileName) {
        this.subject = subject;
        this.email = email;
        this.profileName = profileName;
    }

    public String getSubject() {
        return subject;
    }

    public String getEmail() {
        return email;
    }

    public String getProfileName() {
        return profileName;
    }
}
