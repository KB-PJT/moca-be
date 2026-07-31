package com.moca.mocabe.global.auth;

import java.util.UUID;

/**
 * 인증 완료 후 Security Context에 저장하는 MOCA 사용자 식별자다.
 *
 * <p>사용자 API는 요청 body, query string, header에서 userId를 받지 않고 이 객체만 사용한다.</p>
 */
public final class MocaUserPrincipal {

    private final String userId;

    public MocaUserPrincipal(UUID userId) {
        this(userId == null ? null : userId.toString());
    }

    public MocaUserPrincipal(String userId) {
        this.userId = requireUserId(userId);
    }

    public String getUserId() {
        return userId;
    }

    private String requireUserId(String userId) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 식별자는 UUID여야 합니다.");
        }
        try {
            return UUID.fromString(userId).toString();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("사용자 식별자는 UUID여야 합니다.", exception);
        }
    }
}
