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
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** 토큰 원문 대신 pepper를 포함한 SHA-256 해시만 Redis 키로 사용하는 구현체다. */
public class RedisOpaqueTokenService implements OpaqueTokenService {

    private static final String ACCESS_KEY_PREFIX = "moca:access:";
    private static final String REFRESH_KEY_PREFIX = "moca:refresh:";
    private static final String SESSION_KEY_PREFIX = "moca:session:";
    private static final String USER_SESSIONS_KEY_PREFIX = "moca:user-sessions:";
    private static final String SESSION_TOKENS_KEY_PREFIX = "moca:session-tokens:";
    private static final String REFRESH_ROTATION_SCRIPT = ""
            + "local sessionId = redis.call('GET', KEYS[1])\n"
            + "if not sessionId then return nil end\n"
            + "local sessionKey = ARGV[3] .. sessionId\n"
            + "local sessionValue = redis.call('GET', sessionKey)\n"
            + "if not sessionValue then redis.call('DEL', KEYS[1]); return nil end\n"
            + "redis.call('DEL', KEYS[1])\n"
            + "redis.call('SET', KEYS[2], sessionId, 'PX', ARGV[1])\n"
            + "redis.call('SET', KEYS[3], sessionId, 'PX', ARGV[2])\n"
            + "redis.call('PEXPIRE', sessionKey, ARGV[2])\n"
            + "local tokenKeys = ARGV[4] .. sessionId\n"
            + "redis.call('SREM', tokenKeys, KEYS[1])\n"
            + "redis.call('SADD', tokenKeys, KEYS[2], KEYS[3])\n"
            + "redis.call('PEXPIRE', tokenKeys, ARGV[2])\n"
            + "local separator = string.find(sessionValue, '|')\n"
            + "local userId = string.sub(sessionValue, 1, separator - 1)\n"
            + "local userSessionsKey = ARGV[5] .. userId\n"
            + "redis.call('SADD', userSessionsKey, sessionId)\n"
            + "redis.call('PEXPIRE', userSessionsKey, ARGV[2])\n"
            + "return sessionValue";

    private final StringRedisTemplate redisTemplate;
    private final String pepper;
    private final OpaqueTokenPolicy tokenPolicy;
    private final String hashAlgorithm;
    private final DefaultRedisScript<String> refreshRotationScript;
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
        this.refreshRotationScript = new DefaultRedisScript<String>(REFRESH_ROTATION_SCRIPT, String.class);
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
        trackSessionToken(sessionId, ACCESS_KEY_PREFIX + localTestAccessTokenHash);
        trackUserSession(userId, sessionId);
    }

    @Override
    public OpaqueTokenPair refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            throw new InvalidOpaqueTokenException();
        }
        String accessToken = newToken();
        String newRefreshToken = newToken();
        String sessionValue = redisTemplate.execute(refreshRotationScript,
                java.util.Arrays.asList(refreshKey(refreshToken), accessKey(accessToken), refreshKey(newRefreshToken)),
                Long.toString(tokenPolicy.getAccessTokenTtl().toMillis()),
                Long.toString(tokenPolicy.getRefreshTokenTtl().toMillis()),
                SESSION_KEY_PREFIX, SESSION_TOKENS_KEY_PREFIX, USER_SESSIONS_KEY_PREFIX);
        if (sessionValue == null) {
            throw new InvalidOpaqueTokenException();
        }
        return new OpaqueTokenPair(accessToken, newRefreshToken, tokenPolicy.getAccessTokenTtl().getSeconds());
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
        if (sessionId != null) {
            deleteSession(sessionId);
            return;
        }
        if (accessToken != null) {
            redisTemplate.delete(accessKey(accessToken));
        }
        if (refreshToken != null) {
            redisTemplate.delete(refreshKey(refreshToken));
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
            Set<String> tokenKeys = redisTemplate.opsForSet().members(sessionTokensKey(sessionId));
            if (tokenKeys != null) {
                sessionKeys.addAll(tokenKeys);
            }
            sessionKeys.add(sessionTokensKey(sessionId));
        }
        redisTemplate.delete(sessionKeys);
        redisTemplate.delete(userSessionsKey(userId));
    }

    private OpaqueTokenPair issueForSession(String sessionId) {
        String accessToken = newToken();
        String refreshToken = newToken();
        String accessKey = accessKey(accessToken);
        String refreshKey = refreshKey(refreshToken);
        redisTemplate.opsForValue().set(accessKey, sessionId, tokenPolicy.getAccessTokenTtl());
        redisTemplate.opsForValue().set(refreshKey, sessionId, tokenPolicy.getRefreshTokenTtl());
        trackSessionToken(sessionId, accessKey, refreshKey);
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

    private String sessionTokensKey(String sessionId) {
        return SESSION_TOKENS_KEY_PREFIX + sessionId;
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

    private void trackSessionToken(String sessionId, String... tokenKeys) {
        redisTemplate.opsForSet().add(sessionTokensKey(sessionId), tokenKeys);
        redisTemplate.expire(sessionTokensKey(sessionId), tokenPolicy.getRefreshTokenTtl());
    }

    private void deleteSession(String sessionId) {
        String sessionValue = redisTemplate.opsForValue().get(sessionKey(sessionId));
        Set<String> tokenKeys = redisTemplate.opsForSet().members(sessionTokensKey(sessionId));
        if (tokenKeys != null && !tokenKeys.isEmpty()) {
            redisTemplate.delete(new ArrayList<String>(tokenKeys));
        }
        redisTemplate.delete(sessionTokensKey(sessionId));
        redisTemplate.delete(sessionKey(sessionId));
        if (sessionValue != null) {
            redisTemplate.opsForSet().remove(userSessionsKey(deserialize(sessionValue).getUserId()), sessionId);
        }
    }
}
