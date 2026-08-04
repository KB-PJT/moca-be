package com.moca.mocabe.domain.auth.dto;

import javax.validation.constraints.NotBlank;

/** PWA가 Google callback 뒤 전달하는 PKCE authorization code 요청이다. */
public class GoogleLoginRequest {

    @NotBlank(message = "Google authorization code는 필수입니다.")
    private String code;

    @NotBlank(message = "PKCE code verifier는 필수입니다.")
    private String codeVerifier;

    @NotBlank(message = "Google redirect URI는 필수입니다.")
    private String redirectUri;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getCodeVerifier() {
        return codeVerifier;
    }

    public void setCodeVerifier(String codeVerifier) {
        this.codeVerifier = codeVerifier;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

}
