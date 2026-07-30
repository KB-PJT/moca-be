package com.moca.mocabe.global.auth;

import com.moca.mocabe.global.exception.auth.InvalidOpaqueTokenException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 토큰 원문 대신 pepper를 포함한 SHA-256 해시만 Redis 키로 사용하는 구현체다. */
public class RedisOpaqueTokenService implements OpaqueTokenService {

    private static final String ACCESS_KEY_PREFIX = "moca:access:";
    private static final String REFRESH_KEY_PREFIX = "moca:refresh:";
    private static final String SESSION_KEY_PREFIX = "moca:session:";
    private static final String USER_SESSIONS_KEY_PREFIX = "moca:user-sessions:";

    private final StringRedisTemplate redisTemplate;
    private final String pepper;
    private final OpaqueTokenPolicy tokenPolicy;
    private final String hashAlgorithm;
    private final SecureRandom secureRandom = new SecureRandom();
    private String localTestAccessTokenHash;

    public RedisOpaqueTokenService(StringRedisTemplate redisTemplate, String pepper,
                                   OpaqueTokenPolicy tokenPolicy) {
        this(redisTemplate, pepper, tokenPolicy, "SHA-256");
    }

    RedisOpaqueTokenService(StringRedisTemplate redisTemplate, String pepper,
                            OpaqueTokenPolicy tokenPolicy, String hashAlgorithm) {
        this.redisTemplate = redisTemplate;
        this.pepper = pepper;
        this.tokenPolicy = tokenPolicy;
        this.hashAlgorithm = hashAlgorithm;
    }

    @Override
    public OpaqueTokenPair issue(String userId, String userType) {
        String sessionId = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(sessionKey(sessionId), serialize(userId, userType),
                tokenPolicy.getRefreshTokenTtl());
        trackUserSession(userId, sessionId);
        return issueForSession(sessionId);
    }

    /** local-test 프로필에서 지정한 단일 access token만 인증할 수 있도록 등록한다. */
    public void registerLocalTestAccessToken(String accessToken, String userId, String userType) {
        if (isBlank(accessToken) || isBlank(userId) || isBlank(userType)) {
            throw new IllegalArgumentException("local-test access token, user ID, user type은 필수입니다.");
        }
        String sessionId = UUID.randomUUID().toString();
        localTestAccessTokenHash = hash(accessToken);
        redisTemplate.opsForValue().set(sessionKey(sessionId), serialize(userId, userType),
                tokenPolicy.getAccessTokenTtl());
        redisTemplate.opsForValue().set(ACCESS_KEY_PREFIX + localTestAccessTokenHash, sessionId,
                tokenPolicy.getAccessTokenTtl());
        trackUserSession(userId, sessionId);
    }

    @Override
    public OpaqueTokenPair refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            throw new InvalidOpaqueTokenException();
        }
        String sessionId = redisTemplate.opsForValue().getAndDelete(refreshKey(refreshToken));
        String sessionValue = sessionId == null ? null : redisTemplate.opsForValue().get(sessionKey(sessionId));
        if (sessionValue == null) {
            throw new InvalidOpaqueTokenException();
        }
        AuthenticatedUser user = deserialize(sessionValue);
        redisTemplate.expire(sessionKey(sessionId), tokenPolicy.getRefreshTokenTtl());
        trackUserSession(user.getUserId(), sessionId);
        return issueForSession(sessionId);
    }

    @Override
    public AuthenticatedUser authenticate(String accessToken) {
        if (isBlank(accessToken)) {
            throw new InvalidOpaqueTokenException();
        }
        String accessTokenHash = hash(accessToken);
        if (localTestAccessTokenHash != null && !localTestAccessTokenHash.equals(accessTokenHash)) {
            throw new InvalidOpaqueTokenException();
        }
        String sessionId = redisTemplate.opsForValue().get(ACCESS_KEY_PREFIX + accessTokenHash);
        String sessionValue = sessionId == null ? null : redisTemplate.opsForValue().get(sessionKey(sessionId));
        if (sessionValue == null) {
            throw new InvalidOpaqueTokenException();
        }
        return deserialize(sessionValue);
    }

    @Override
    public void revoke(String accessToken, String refreshToken) {
        String sessionId = sessionId(accessToken, ACCESS_KEY_PREFIX);
        if (sessionId == null) {
            sessionId = sessionId(refreshToken, REFRESH_KEY_PREFIX);
        }
        if (accessToken != null) {
            redisTemplate.delete(accessKey(accessToken));
        }
        if (refreshToken != null) {
            redisTemplate.delete(refreshKey(refreshToken));
        }
        if (sessionId != null) {
            deleteSession(sessionId);
        }
    }

    @Override
    public void revokeAll(String userId) {
        Set<String> sessionIds = redisTemplate.opsForSet().members(userSessionsKey(userId));
        if (sessionIds == null || sessionIds.isEmpty()) {
            return;
        }
        List<String> sessionKeys = new ArrayList<String>();
        for (String sessionId : sessionIds) {
            sessionKeys.add(sessionKey(sessionId));
        }
        redisTemplate.delete(sessionKeys);
        redisTemplate.delete(userSessionsKey(userId));
    }

    private OpaqueTokenPair issueForSession(String sessionId) {
        String accessToken = newToken();
        String refreshToken = newToken();
        redisTemplate.opsForValue().set(accessKey(accessToken), sessionId, tokenPolicy.getAccessTokenTtl());
        redisTemplate.opsForValue().set(refreshKey(refreshToken), sessionId, tokenPolicy.getRefreshTokenTtl());
        return new OpaqueTokenPair(accessToken, refreshToken, tokenPolicy.getAccessTokenTtl().getSeconds());
    }

    private String sessionId(String token, String keyPrefix) {
        return token == null ? null : redisTemplate.opsForValue().get(keyPrefix + hash(token));
    }

    private String accessKey(String token) {
        return ACCESS_KEY_PREFIX + hash(token);
    }

    private String refreshKey(String token) {
        return REFRESH_KEY_PREFIX + hash(token);
    }

    private String sessionKey(String sessionId) {
        return SESSION_KEY_PREFIX + sessionId;
    }

    private String userSessionsKey(String userId) {
        return USER_SESSIONS_KEY_PREFIX + userId;
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance(hashAlgorithm);
            byte[] hashed = digest.digest((token + pepper).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }

    private String serialize(String userId, String userType) {
        return userId + "|" + userType;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private AuthenticatedUser deserialize(String sessionValue) {
        String[] values = sessionValue.split("\\|", -1);
        if (values.length != 2 || values[0].trim().isEmpty() || values[1].trim().isEmpty()) {
            throw new InvalidOpaqueTokenException();
        }
        return new AuthenticatedUser(values[0], values[1]);
    }

    private void trackUserSession(String userId, String sessionId) {
        redisTemplate.opsForSet().add(userSessionsKey(userId), sessionId);
        redisTemplate.expire(userSessionsKey(userId), tokenPolicy.getRefreshTokenTtl());
    }

    private void deleteSession(String sessionId) {
        String sessionValue = redisTemplate.opsForValue().get(sessionKey(sessionId));
        redisTemplate.delete(sessionKey(sessionId));
        if (sessionValue != null) {
            redisTemplate.opsForSet().remove(userSessionsKey(deserialize(sessionValue).getUserId()), sessionId);
        }
    }
}
