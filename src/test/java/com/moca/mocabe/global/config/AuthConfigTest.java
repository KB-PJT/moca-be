package com.moca.mocabe.global.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.moca.mocabe.global.auth.OpaqueTokenPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
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
