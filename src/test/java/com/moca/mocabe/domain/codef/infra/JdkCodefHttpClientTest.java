package com.moca.mocabe.domain.codef.infra;

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

class JdkCodefHttpClientTest {

    private HttpClient httpClient;
    private HttpResponse<String> httpResponse;
    private JdkCodefHttpClient codefHttpClient;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        httpClient = org.mockito.Mockito.mock(HttpClient.class);
        httpResponse = org.mockito.Mockito.mock(HttpResponse.class);
        codefHttpClient = new JdkCodefHttpClient(httpClient, Duration.ofSeconds(7));
    }

    @Test
    @DisplayName("싱글턴 JDK 클라이언트로 요청 타임아웃을 적용해 전송한다")
    void sendsRequestWithTimeout() throws Exception {
        when(httpResponse.statusCode()).thenReturn(201);
        when(httpResponse.body()).thenReturn("response-body");
        when(httpClient.send(any(HttpRequest.class), anyStringBodyHandler())).thenReturn(httpResponse);

        CodefHttpResponse response = codefHttpClient.post(
                "https://codef.example.com/path", Map.of("X-Test", "header"), "request-body");

        assertEquals(201, response.statusCode());
        assertEquals("response-body", response.body());
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), anyStringBodyHandler());
        HttpRequest request = requestCaptor.getValue();
        assertEquals(Duration.ofSeconds(7), request.timeout().orElseThrow());
        assertEquals("header", request.headers().firstValue("X-Test").orElseThrow());
    }

    @Test
    @DisplayName("네트워크 입출력 오류를 CODEF 요청 오류로 변환한다")
    void convertsIOException() throws Exception {
        when(httpClient.send(any(HttpRequest.class), anyStringBodyHandler()))
                .thenThrow(new IOException("network"));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> codefHttpClient.post("https://codef.example.com", Map.of(), "body"));

        assertEquals("CODEF 요청에 실패했습니다.", exception.getMessage());
    }

    @Test
    @DisplayName("요청 중단 시 인터럽트 상태를 복구하고 오류로 변환한다")
    void restoresInterruptWhenInterrupted() throws Exception {
        when(httpClient.send(any(HttpRequest.class), anyStringBodyHandler()))
                .thenThrow(new InterruptedException("interrupted"));

        try {
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> codefHttpClient.post("https://codef.example.com", Map.of(), "body"));

            assertEquals("CODEF 요청이 중단되었습니다.", exception.getMessage());
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
