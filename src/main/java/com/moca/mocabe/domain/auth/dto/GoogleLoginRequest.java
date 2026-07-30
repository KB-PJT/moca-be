package com.moca.mocabe.domain.auth.dto;

import javax.validation.constraints.NotBlank;

/** PWA가 PKCE 교환 뒤 전달하는 Google ID Token 요청이다. */
public class GoogleLoginRequest {

    @NotBlank(message = "Google ID Token은 필수입니다.")
    private String idToken;

    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }
}
