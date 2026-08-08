package com.moca.mocabe.domain.merchant.infra;

import com.moca.mocabe.global.exception.merchant.KakaoUnavailableException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/** JDK HttpClient를 재사용해 카카오맵 로컬 API HTTP 요청을 전송한다. */
public class JdkKakaoHttpClient implements KakaoHttpClient {

    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public JdkKakaoHttpClient(HttpClient httpClient, Duration requestTimeout) {
        this.httpClient = Objects.requireNonNull(httpClient);
        this.requestTimeout = Objects.requireNonNull(requestTimeout);
    }

    @Override
    public KakaoHttpResponse get(String url, Map<String, String> headers) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(requestTimeout)
                    .GET();
            headers.forEach(builder::header);
            HttpResponse<String> response = httpClient.send(
                    builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new KakaoHttpResponse(response.statusCode(), response.body());
        } catch (HttpTimeoutException exception) {
            throw new KakaoUnavailableException(
                    "카카오맵 응답이 지연되어 처리하지 못했습니다. 잠시 후 다시 시도해주세요.", exception);
        } catch (IOException exception) {
            throw new KakaoUnavailableException(
                    "카카오맵 서비스에 연결하지 못했습니다. 잠시 후 다시 시도해주세요.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("카카오맵 요청이 중단되었습니다.", exception);
        }
    }
}
