package com.moca.mocabe.global.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JdkGoogleOAuthHttpClientTest {

    private HttpClient httpClient;
    private HttpResponse<String> httpResponse;
    private JdkGoogleOAuthHttpClient googleOAuthHttpClient;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        httpClient = org.mockito.Mockito.mock(HttpClient.class);
        httpResponse = org.mockito.Mockito.mock(HttpResponse.class);
        googleOAuthHttpClient = new JdkGoogleOAuthHttpClient(httpClient, Duration.ofSeconds(7));
    }

    @Test
    @DisplayName("token 교환 form과 Google GET 요청에 타임아웃·Authorization 헤더를 적용한다")
    void sendsFormAndGetRequests() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("response-body");
        when(httpClient.send(any(HttpRequest.class), anyStringBodyHandler())).thenReturn(httpResponse);

        GoogleOAuthHttpResponse formResponse = googleOAuthHttpClient.postForm("https://google.example.com/token",
                Map.of("code", "code value"));
        GoogleOAuthHttpResponse getResponse = googleOAuthHttpClient.get("https://google.example.com/tokeninfo");
        GoogleOAuthHttpResponse authorizedGetResponse = googleOAuthHttpClient.get("https://google.example.com/userinfo",
                Map.of("Authorization", "Bearer access-token"));

        assertEquals(200, formResponse.getStatusCode());
        assertEquals("response-body", getResponse.getBody());
        assertEquals("response-body", authorizedGetResponse.getBody());
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, org.mockito.Mockito.times(3)).send(requestCaptor.capture(), anyStringBodyHandler());
        HttpRequest formRequest = requestCaptor.getAllValues().get(0);
        HttpRequest getRequest = requestCaptor.getAllValues().get(1);
        HttpRequest authorizedGetRequest = requestCaptor.getAllValues().get(2);
        assertEquals(Duration.ofSeconds(7), formRequest.timeout().orElseThrow());
        assertEquals("application/x-www-form-urlencoded",
                formRequest.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("GET", getRequest.method());
        assertEquals("Bearer access-token", authorizedGetRequest.headers()
                .firstValue("Authorization").orElseThrow());
    }

    @Test
    @DisplayName("네트워크 입출력 오류를 Google OAuth 요청 오류로 변환한다")
    void convertsIOException() throws Exception {
        when(httpClient.send(any(HttpRequest.class), anyStringBodyHandler()))
                .thenThrow(new IOException("network"));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> googleOAuthHttpClient.get("https://google.example.com"));

        assertEquals("Google OAuth 요청에 실패했습니다.", exception.getMessage());
    }

    @Test
    @DisplayName("요청 중단 시 인터럽트 상태를 복구하고 오류로 변환한다")
    void restoresInterruptWhenInterrupted() throws Exception {
        when(httpClient.send(any(HttpRequest.class), anyStringBodyHandler()))
                .thenThrow(new InterruptedException("interrupted"));

        try {
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> googleOAuthHttpClient.get("https://google.example.com"));

            assertEquals("Google OAuth 요청이 중단되었습니다.", exception.getMessage());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @SuppressWarnings("unchecked")
    private HttpResponse.BodyHandler<String> anyStringBodyHandler() {
        return any(HttpResponse.BodyHandler.class);
    }
}
