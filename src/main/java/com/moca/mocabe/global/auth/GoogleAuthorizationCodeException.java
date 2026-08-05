package com.moca.mocabe.global.auth;

/** Google authorization code 교환 또는 access token 검증에 실패했을 때 발생한다. */
public class GoogleAuthorizationCodeException extends RuntimeException {

    public GoogleAuthorizationCodeException() {
        super("유효하지 않은 Google authorization code 또는 access token입니다.");
    }
}
