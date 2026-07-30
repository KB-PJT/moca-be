package com.moca.mocabe.global.auth;

import java.time.Duration;

/** 환경 설정으로 관리하는 MOCA opaque access·refresh token 만료 정책이다. */
public class OpaqueTokenPolicy {

    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public OpaqueTokenPolicy(long accessTokenTtlSeconds, long refreshTokenTtlSeconds) {
        if (accessTokenTtlSeconds <= 0 || refreshTokenTtlSeconds <= 0) {
            throw new IllegalArgumentException("토큰 만료 시간은 1초 이상이어야 합니다.");
        }
        this.accessTokenTtl = Duration.ofSeconds(accessTokenTtlSeconds);
        this.refreshTokenTtl = Duration.ofSeconds(refreshTokenTtlSeconds);
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    public long getRefreshTokenTtlSeconds() {
        return refreshTokenTtl.getSeconds();
    }
}
