package com.moca.mocabe.global.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;

import com.moca.mocabe.global.exception.auth.InvalidOpaqueTokenException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

class RedisOpaqueTokenServiceTest {

    private static final String USER_ID = "01980d6a-5c0c-7aaf-9b85-010203040506";

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private SetOperations<String, String> setOperations;
    private RedisOpaqueTokenService tokenService;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
        valueOperations = org.mockito.Mockito.mock(ValueOperations.class);
        setOperations = org.mockito.Mockito.mock(SetOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        tokenService = new RedisOpaqueTokenService(redisTemplate, "test-pepper",
                new OpaqueTokenPolicy(1800, 1209600));
    }

    @Test
    @DisplayName("refresh token은 Lua 스크립트로 원자적으로 회전하고 새 토큰 쌍을 발급한다")
    void rotatesRefreshTokenAtomically() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(),
                any(Object[].class)))
                .thenReturn(USER_ID + "|user");

        OpaqueTokenPair tokenPair = tokenService.refresh("refresh-token");

        verify(redisTemplate).execute(any(org.springframework.data.redis.core.script.DefaultRedisScript.class), any(),
                any(Object[].class));
        org.junit.jupiter.api.Assertions.assertEquals(1800, tokenPair.getAccessTokenExpiresIn());
    }

    @Test
    @DisplayName("refresh 회전 Lua 스크립트는 사용자 세션 인덱스의 TTL도 함께 연장한다")
    void extendsUserSessionIndexDuringRefresh() {
        when(redisTemplate.execute(any(org.springframework.data.redis.core.script.DefaultRedisScript.class), any(),
                any(Object[].class))).thenReturn(USER_ID + "|user");

        tokenService.refresh("refresh-token");

        org.mockito.ArgumentCaptor<DefaultRedisScript> scriptCaptor =
                org.mockito.ArgumentCaptor.forClass(DefaultRedisScript.class);
        verify(redisTemplate).execute(scriptCaptor.capture(), any(), any(Object[].class));
        assertTrue(scriptCaptor.getValue().getScriptAsString().contains("userSessionsKey"));
        assertTrue(scriptCaptor.getValue().getScriptAsString().contains("PEXPIRE', userSessionsKey"));
    }

    @Test
    @DisplayName("이미 소비했거나 존재하지 않는 refresh token은 재발급에 사용할 수 없다")
    void rejectsConsumedRefreshToken() {
        when(redisTemplate.execute(any(org.springframework.data.redis.core.script.DefaultRedisScript.class), any(),
                any(Object[].class)))
                .thenReturn(null);

        assertThrows(InvalidOpaqueTokenException.class, () -> tokenService.refresh("used-token"));
    }

    @Test
    @DisplayName("사용자 탈퇴 시 사용자에게 연결된 모든 Redis 세션을 삭제한다")
    void revokesAllUserSessions() {
        when(setOperations.members("moca:user-sessions:" + USER_ID))
                .thenReturn(new LinkedHashSet<String>(Arrays.asList("session-1", "session-2")));

        assertDoesNotThrow(() -> tokenService.revokeAll(USER_ID));

        verify(redisTemplate).delete(Arrays.asList("moca:session:session-1", "moca:session-tokens:session-1",
                "moca:session:session-2", "moca:session-tokens:session-2"));
        verify(redisTemplate).delete("moca:user-sessions:" + USER_ID);
    }

    @Test
    @DisplayName("신규 세션은 토큰 원문 대신 해시 키로 access·refresh 토큰을 저장한다")
    void issuesTokensForNewSession() {
        OpaqueTokenPair tokenPair = tokenService.issue(USER_ID, "user");

        org.junit.jupiter.api.Assertions.assertFalse(tokenPair.getAccessToken().isEmpty());
        org.junit.jupiter.api.Assertions.assertFalse(tokenPair.getRefreshToken().isEmpty());
        verify(valueOperations, times(3)).set(anyString(), anyString(), any(Duration.class));
        verify(setOperations).add(org.mockito.ArgumentMatchers.eq("moca:user-sessions:" + USER_ID),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("세션이 존재하는 access token은 인증 정보를 복원하고 없거나 손상된 세션은 거절한다")
    void authenticatesOnlyValidSession() {
        when(valueOperations.get(anyString())).thenReturn("session-1", USER_ID + "|user");

        AuthenticatedUser user = tokenService.authenticate("access-token");

        org.junit.jupiter.api.Assertions.assertEquals(USER_ID, user.getUserId());
        assertThrows(InvalidOpaqueTokenException.class, () -> tokenService.authenticate(" "));
        when(valueOperations.get(anyString())).thenReturn(null);
        assertThrows(InvalidOpaqueTokenException.class, () -> tokenService.authenticate("expired-token"));
        when(valueOperations.get(anyString())).thenReturn("session-2", "invalid-session-value");
        assertThrows(InvalidOpaqueTokenException.class, () -> tokenService.authenticate("broken-token"));
    }

    @Test
    @DisplayName("세션 폐기 시 access 또는 refresh token에서 세션을 찾아 사용자 인덱스까지 제거한다")
    void revokesSessionFromEitherToken() {
        when(valueOperations.get(anyString()))
                .thenReturn(null, "session-1", USER_ID + "|user");
        when(setOperations.members("moca:session-tokens:session-1"))
                .thenReturn(new LinkedHashSet<String>(Collections.singleton("moca:access:hashed-token")));

        tokenService.revoke("access-token", "refresh-token");

        verify(redisTemplate).delete(Collections.singletonList("moca:access:hashed-token"));
        verify(redisTemplate).delete("moca:session-tokens:session-1");
        verify(redisTemplate).delete("moca:session:session-1");
        verify(setOperations).remove("moca:user-sessions:" + USER_ID, "session-1");
    }

    @Test
    @DisplayName("세션을 찾지 못한 로그아웃은 전달된 토큰 키만 개별적으로 폐기한다")
    void revokesProvidedTokensWhenSessionIsMissing() {
        when(valueOperations.get(anyString())).thenReturn(null);

        tokenService.revoke("access-token", "refresh-token");
        tokenService.revoke(null, null);

        verify(redisTemplate).delete(org.mockito.ArgumentMatchers.startsWith("moca:access:"));
        verify(redisTemplate).delete(org.mockito.ArgumentMatchers.startsWith("moca:refresh:"));
    }

    @Test
    @DisplayName("빈 refresh token, 만료 세션, 비어 있는 사용자 세션 목록은 안전하게 거절 또는 무시한다")
    void handlesMissingTokenAndSessionValues() {
        assertThrows(InvalidOpaqueTokenException.class, () -> tokenService.refresh(null));
        assertThrows(InvalidOpaqueTokenException.class, () -> tokenService.refresh("  "));
        when(redisTemplate.execute(any(org.springframework.data.redis.core.script.DefaultRedisScript.class), any(),
                any(Object[].class)))
                .thenReturn(null);
        assertThrows(InvalidOpaqueTokenException.class, () -> tokenService.refresh("expired"));

        when(setOperations.members("moca:user-sessions:" + USER_ID)).thenReturn(Collections.<String>emptySet());
        assertDoesNotThrow(() -> tokenService.revokeAll(USER_ID));
    }

    @Test
    @DisplayName("지원하지 않는 해시 알고리즘은 서버 설정 오류로 처리한다")
    void rejectsUnsupportedHashAlgorithm() {
        RedisOpaqueTokenService invalidHashService = new RedisOpaqueTokenService(redisTemplate, "test-pepper",
                new OpaqueTokenPolicy(1800, 1209600), "NOT_A_HASH_ALGORITHM");

        assertThrows(IllegalStateException.class, () -> invalidHashService.issue(USER_ID, "user"));
    }
}
