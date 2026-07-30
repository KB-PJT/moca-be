package com.moca.mocabe.global.exception.auth;

/** 인증 정보가 없거나 MOCA 사용자 식별자로 해석할 수 없을 때 발생한다. */
public class AuthenticationRequiredException extends RuntimeException {

    public AuthenticationRequiredException() {
        super("인증이 필요합니다.");
    }
}
