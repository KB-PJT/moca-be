package com.moca.mocabe.global.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.moca.mocabe.global.auth.OpaqueTokenPolicy;
import com.moca.mocabe.global.auth.RedisOpaqueTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

class AuthConfigTest {

    private final AuthConfig authConfig = new AuthConfig();

    @Test
    @DisplayName("local 환경은 개발용 token hash pepper 기본값을 사용할 수 있다")
    void allowsDefaultPepperOnlyInLocal() {
        MockEnvironment environment = new MockEnvironment().withProperty("MOCA_PROFILE", "local");
        environment.setActiveProfiles("local");

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
    @DisplayName("local-test 고정 token 등록은 필요한 설정값 없이 시작할 수 없다")
    void rejectsIncompleteLocalTestConfiguration() throws Exception {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("MOCA_PROFILE", "local-test")
                .withProperty("MOCA_TOKEN_HASH_PEPPER", "test-pepper");

        RedisOpaqueTokenService tokenService = authConfig.opaqueTokenService(new StringRedisTemplate(), environment,
                new OpaqueTokenPolicy(1800, 1209600));
        assertThrows(IllegalStateException.class, () -> new LocalTestAuthConfig()
                .localTestAccessTokenRegistrar(tokenService, environment).afterPropertiesSet());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("local-test 환경은 지정한 access token 하나를 Redis에 해시로 등록한다")
    void registersFixedLocalTestAccessToken() throws Exception {
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

        RedisOpaqueTokenService tokenService = authConfig.opaqueTokenService(redisTemplate, environment,
                new OpaqueTokenPolicy(1800, 1209600));
        new LocalTestAuthConfig().localTestAccessTokenRegistrar(tokenService, environment).afterPropertiesSet();
        Mockito.verify(valueOperations, Mockito.times(2)).set(Mockito.anyString(), Mockito.anyString(),
                Mockito.any(java.time.Duration.class));
    }

    @Test
    @DisplayName("Google OAuth client 설정이 없으면 authorization code 교환기를 생성하지 않는다")
    void rejectsMissingGoogleOAuthClientConfiguration() {
        assertThrows(IllegalStateException.class,
                () -> authConfig.googleAuthorizationCodeExchanger(Mockito.mock(
                        com.moca.mocabe.global.auth.GoogleOAuthHttpClient.class), new MockEnvironment()));
    }

    @Test
    @DisplayName("Google Client ID와 Secret을 설정하면 authorization code 교환기를 생성한다")
    void createsGoogleAuthorizationCodeExchanger() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("MOCA_GOOGLE_CLIENT_ID", "client-id")
                .withProperty("MOCA_GOOGLE_CLIENT_SECRET", "client-secret")
                .withProperty("MOCA_GOOGLE_ALLOWED_REDIRECT_URIS",
                        "http://localhost:5173/auth/callback,https://moca-fe-rho.vercel.app/auth/callback");

        assertNotNull(authConfig.googleAuthorizationCodeExchanger(Mockito.mock(
                com.moca.mocabe.global.auth.GoogleOAuthHttpClient.class), environment));
    }

    @Test
    @DisplayName("루트 Security 컨텍스트도 프론트 origin의 credential CORS 설정을 사용한다")
    void configuresCorsForFrontendOrigins() {
        org.springframework.web.cors.CorsConfiguration configuration = authConfig.corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest("OPTIONS", "/api/v1/auth/refresh"));

        org.junit.jupiter.api.Assertions.assertEquals(
                java.util.Arrays.asList("http://localhost:5173", "https://moca-fe-rho.vercel.app"),
                configuration.getAllowedOrigins());
        org.junit.jupiter.api.Assertions.assertEquals(Boolean.TRUE, configuration.getAllowCredentials());
    }
}
