package com.moca.mocabe.global.exception.auth;

/** opaque access 또는 refresh token이 없거나 Redis 세션과 일치하지 않을 때 발생한다. */
public class InvalidOpaqueTokenException extends RuntimeException {

    public InvalidOpaqueTokenException() {
        super("유효하지 않거나 만료된 토큰입니다.");
    }
}
