package com.moca.mocabe.global.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.moca.mocabe.global.auth.OpaqueTokenPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.env.MockEnvironment;

class AuthConfigTest {

    private final AuthConfig authConfig = new AuthConfig();

    @Test
    @DisplayName("local 환경은 개발용 token hash pepper 기본값을 사용할 수 있다")
    void allowsDefaultPepperOnlyInLocal() {
        MockEnvironment environment = new MockEnvironment().withProperty("MOCA_PROFILE", "local");

        assertNotNull(authConfig.opaqueTokenService(new StringRedisTemplate(), environment,
                new OpaqueTokenPolicy(1800, 1209600)));
    }

    @Test
    @DisplayName("local 이외 환경은 token hash pepper 없이 시작할 수 없다")
    void rejectsMissingPepperOutsideLocal() {
        MockEnvironment environment = new MockEnvironment().withProperty("MOCA_PROFILE", "production");

        assertThrows(IllegalStateException.class, () -> authConfig.opaqueTokenService(new StringRedisTemplate(),
                environment, new OpaqueTokenPolicy(1800, 1209600)));
    }

    @Test
    @DisplayName("local-test 환경은 고정 access token과 사용자 ID 없이 시작할 수 없다")
    void rejectsIncompleteLocalTestConfiguration() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("MOCA_PROFILE", "local-test")
                .withProperty("MOCA_TOKEN_HASH_PEPPER", "test-pepper");

        assertThrows(IllegalStateException.class, () -> authConfig.opaqueTokenService(new StringRedisTemplate(),
                environment, new OpaqueTokenPolicy(1800, 1209600)));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("local-test 환경은 지정한 access token 하나를 Redis에 해시로 등록한다")
    void registersFixedLocalTestAccessToken() {
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);
        SetOperations<String, String> setOperations = Mockito.mock(SetOperations.class);
        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        Mockito.when(redisTemplate.opsForSet()).thenReturn(setOperations);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("MOCA_PROFILE", "local-test")
                .withProperty("MOCA_TOKEN_HASH_PEPPER", "test-pepper")
                .withProperty("MOCA_LOCAL_TEST_ACCESS_TOKEN", "fixed-token")
                .withProperty("MOCA_LOCAL_TEST_USER_ID", "01980d6a-5c0c-7aaf-9b85-010203040506");

        assertNotNull(authConfig.opaqueTokenService(redisTemplate, environment,
                new OpaqueTokenPolicy(1800, 1209600)));
        Mockito.verify(valueOperations, Mockito.times(2)).set(Mockito.anyString(), Mockito.anyString(),
                Mockito.any(java.time.Duration.class));
    }

    @Test
    @DisplayName("Google Client ID가 없으면 인증 검증기를 생성하지 않는다")
    void rejectsMissingGoogleClientId() {
        assertThrows(IllegalStateException.class,
                () -> authConfig.googleIdTokenVerifier(new MockEnvironment()));
    }
}
