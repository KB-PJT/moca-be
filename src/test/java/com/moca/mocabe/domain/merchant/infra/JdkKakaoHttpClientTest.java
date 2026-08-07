package com.moca.mocabe.domain.merchant.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moca.mocabe.global.exception.merchant.KakaoUnavailableException;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JdkKakaoHttpClientTest {

    private HttpClient httpClient;
    private HttpResponse<String> httpResponse;
    private JdkKakaoHttpClient kakaoHttpClient;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        httpClient = org.mockito.Mockito.mock(HttpClient.class);
        httpResponse = org.mockito.Mockito.mock(HttpResponse.class);
        kakaoHttpClient = new JdkKakaoHttpClient(httpClient, Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("타임아웃과 헤더를 적용해 GET 요청을 전송한다")
    void sendsGetRequestWithTimeoutAndHeaders() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"documents\":[]}");
        when(httpClient.send(any(HttpRequest.class), anyStringBodyHandler())).thenReturn(httpResponse);

        KakaoHttpResponse response = kakaoHttpClient.get(
                "https://dapi.kakao.com/v2/local/search/category.json", Map.of("Authorization", "KakaoAK key"));

        assertEquals(200, response.statusCode());
        assertEquals("{\"documents\":[]}", response.body());
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), anyStringBodyHandler());
        HttpRequest request = requestCaptor.getValue();
        assertEquals(Duration.ofSeconds(5), request.timeout().orElseThrow());
        assertEquals("KakaoAK key", request.headers().firstValue("Authorization").orElseThrow());
        assertEquals("GET", request.method());
    }

    @Test
    @DisplayName("응답 타임아웃은 재시도 안내가 담긴 카카오맵 일시 장애 오류로 변환한다")
    void convertsTimeoutToUnavailable() throws Exception {
        when(httpClient.send(any(HttpRequest.class), anyStringBodyHandler()))
                .thenThrow(new HttpTimeoutException("timeout"));

        KakaoUnavailableException exception = assertThrows(KakaoUnavailableException.class,
                () -> kakaoHttpClient.get("https://dapi.kakao.com/v2/local/search/category.json", Map.of()));

        assertTrue(exception.getMessage().contains("지연"));
    }

    @Test
    @DisplayName("네트워크 입출력 오류를 카카오맵 일시 장애 오류로 변환한다")
    void convertsIOException() throws Exception {
        when(httpClient.send(any(HttpRequest.class), anyStringBodyHandler()))
                .thenThrow(new IOException("network"));

        KakaoUnavailableException exception = assertThrows(KakaoUnavailableException.class,
                () -> kakaoHttpClient.get("https://dapi.kakao.com/v2/local/search/category.json", Map.of()));

        assertTrue(exception.getMessage().contains("연결"));
    }

    @Test
    @DisplayName("요청 중단 시 인터럽트 상태를 복구하고 오류로 변환한다")
    void restoresInterruptWhenInterrupted() throws Exception {
        when(httpClient.send(any(HttpRequest.class), anyStringBodyHandler()))
                .thenThrow(new InterruptedException("interrupted"));

        try {
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> kakaoHttpClient.get("https://dapi.kakao.com/v2/local/search/category.json", Map.of()));

            assertEquals("카카오맵 요청이 중단되었습니다.", exception.getMessage());
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
