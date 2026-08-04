package com.moca.mocabe.global.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.moca.mocabe.global.exception.auth.AuthenticationRequiredException;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AuthenticationSupportTest {

    private static final String USER_ID = "01980d6a-5c0c-7aaf-9b85-010203040506";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("인증된 MOCA principal에서 UUID 형식의 현재 사용자 식별자를 읽는다")
    void getsCurrentUserId() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new MocaUserPrincipal(USER_ID), null, Collections.emptyList()));

        assertEquals(USER_ID, new SecurityContextCurrentUserProvider().getCurrentUserId());
    }

    @Test
    @DisplayName("인증 정보 또는 MOCA principal이 없으면 현재 사용자 조회를 거절한다")
    void rejectsMissingOrInvalidPrincipal() {
        SecurityContextCurrentUserProvider provider = new SecurityContextCurrentUserProvider();
        assertThrows(AuthenticationRequiredException.class, provider::getCurrentUserId);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user", null));
        assertThrows(AuthenticationRequiredException.class, provider::getCurrentUserId);
    }

    @Test
    @DisplayName("principal은 UUID 문자열과 UUID 객체를 정규화하고 유효하지 않은 값은 거절한다")
    void validatesMocaUserPrincipal() {
        assertEquals(USER_ID, new MocaUserPrincipal(java.util.UUID.fromString(USER_ID)).getUserId());
        assertEquals(USER_ID, new MocaUserPrincipal(USER_ID).getUserId());
        assertThrows(IllegalArgumentException.class, () -> new MocaUserPrincipal((String) null));
        assertThrows(IllegalArgumentException.class, () -> new MocaUserPrincipal("not-a-uuid"));
    }

    @Test
    @DisplayName("세션 사용자 정보와 토큰 정책은 값을 보존하고 유효하지 않은 TTL을 거절한다")
    void keepsSessionValuesAndValidatesTokenTtl() {
        AuthenticatedUser user = new AuthenticatedUser(USER_ID, "user");
        OpaqueTokenPolicy policy = new OpaqueTokenPolicy(1800, 1209600);

        assertEquals(USER_ID, user.getUserId());
        assertEquals("user", user.getUserType());
        assertEquals(1800, policy.getAccessTokenTtl().getSeconds());
        assertEquals(1209600, policy.getRefreshTokenTtl().getSeconds());
        assertEquals(1209600, policy.getRefreshTokenTtlSeconds());
        assertThrows(IllegalArgumentException.class, () -> new OpaqueTokenPolicy(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new OpaqueTokenPolicy(1, 0));
    }
}
