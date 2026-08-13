package com.moca.mocabe.global.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.moca.mocabe.domain.codef.infra.CodefHttpClient;
import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class AppConfigCodefTest {

    private final AppConfig appConfig = new AppConfig();

    @Test
    @DisplayName("루트 컨텍스트에서 사용할 JSON 매퍼를 제공한다")
    void providesObjectMapper() {
        assertNotNull(appConfig.objectMapper());
    }

    @Test
    @DisplayName("CODEF 기본 URL이 없으면 클라이언트 조립에 실패한다")
    void requiresCodefBaseUrl() {
        MockEnvironment environment = codefEnvironment()
                .withProperty("MOCA_CODEF_TOKEN_URL", "https://oauth.codef.example.com/token");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> appConfig.codefClient(org.mockito.Mockito.mock(CodefHttpClient.class), environment));

        assertEquals("MOCA_CODEF_BASE_URL 환경변수가 필요합니다.", exception.getMessage());
    }

    @Test
    @DisplayName("CODEF 연결 타임아웃을 싱글턴 HttpClient 설정에 적용한다")
    void configuresConnectTimeout() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("MOCA_CODEF_CONNECT_TIMEOUT_MS", "4500");

        HttpClient httpClient = appConfig.codefJavaHttpClient(environment);

        assertEquals(Duration.ofMillis(4500), httpClient.connectTimeout().orElseThrow());
    }

    @Test
    @DisplayName("CODEF HttpClient는 리다이렉트를 자동으로 따라가지 않는다")
    void neverFollowsRedirects() {
        HttpClient httpClient = appConfig.codefJavaHttpClient(new MockEnvironment());

        assertEquals(HttpClient.Redirect.NEVER, httpClient.followRedirects());
    }

    @Test
    @DisplayName("baseUrl이 https가 아니면 CODEF 클라이언트 조립에 실패한다")
    void rejectsNonHttpsBaseUrl() {
        MockEnvironment environment = codefEnvironment()
                .withProperty("MOCA_CODEF_BASE_URL", "http://development.codef.io")
                .withProperty("MOCA_CODEF_TOKEN_URL", "https://oauth.codef.io/oauth/token");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> appConfig.codefClient(org.mockito.Mockito.mock(CodefHttpClient.class), environment));

        assertEquals("MOCA_CODEF_BASE_URL은(는) https URL이어야 합니다.", exception.getMessage());
    }

    @Test
    @DisplayName("승인되지 않은 host면 CODEF 클라이언트 조립에 실패한다")
    void rejectsDisallowedHost() {
        MockEnvironment environment = codefEnvironment()
                .withProperty("MOCA_CODEF_BASE_URL", "https://development.codef.io")
                .withProperty("MOCA_CODEF_TOKEN_URL", "https://attacker.example.com/oauth/token");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> appConfig.codefClient(org.mockito.Mockito.mock(CodefHttpClient.class), environment));

        assertEquals("MOCA_CODEF_TOKEN_URL의 host가 승인된 CODEF host가 아닙니다: attacker.example.com",
                exception.getMessage());
    }

    @Test
    @DisplayName("승인된 CODEF host면 클라이언트를 정상 조립한다")
    void acceptsApprovedCodefHosts() {
        MockEnvironment environment = codefEnvironment()
                .withProperty("MOCA_CODEF_BASE_URL", "https://development.codef.io")
                .withProperty("MOCA_CODEF_TOKEN_URL", "https://oauth.codef.io/oauth/token");

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> appConfig.codefClient(org.mockito.Mockito.mock(CodefHttpClient.class), environment));
    }

    @Test
    @DisplayName("0 이하의 CODEF 요청 타임아웃은 거부한다")
    void rejectsNonPositiveRequestTimeout() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("MOCA_CODEF_REQUEST_TIMEOUT_MS", "0");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> appConfig.codefHttpClient(
                        org.mockito.Mockito.mock(HttpClient.class), environment));

        assertEquals("MOCA_CODEF_REQUEST_TIMEOUT_MS 환경변수는 1 이상이어야 합니다.",
                exception.getMessage());
    }

    private MockEnvironment codefEnvironment() {
        return new MockEnvironment()
                .withProperty("MOCA_CODEF_CLIENT_ID", "client-id")
                .withProperty("MOCA_CODEF_CLIENT_SECRET", "client-secret")
                .withProperty("MOCA_CODEF_PUBLIC_KEY", "public-key");
    }
}
