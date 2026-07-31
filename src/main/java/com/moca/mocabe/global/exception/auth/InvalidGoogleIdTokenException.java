package com.moca.mocabe.global.exception.auth;

/** Google ID Token의 서명, 발급자, 대상 또는 만료 검증에 실패했을 때 발생한다. */
public class InvalidGoogleIdTokenException extends RuntimeException {

    public InvalidGoogleIdTokenException() {
        super("유효하지 않은 Google ID Token입니다.");
    }
}
