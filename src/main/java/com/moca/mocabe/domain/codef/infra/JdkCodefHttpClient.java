package com.moca.mocabe.domain.codef.infra;

import com.moca.mocabe.domain.codef.exception.CodefUnavailableException;
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

/** JDK HttpClient를 재사용해 CODEF HTTP 요청을 전송한다. */
public class JdkCodefHttpClient implements CodefHttpClient {

    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public JdkCodefHttpClient(HttpClient httpClient, Duration requestTimeout) {
        this.httpClient = Objects.requireNonNull(httpClient);
        this.requestTimeout = Objects.requireNonNull(requestTimeout);
    }

    @Override
    public CodefHttpResponse post(String url, Map<String, String> headers, String body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(requestTimeout)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            headers.forEach(builder::header);
            HttpResponse<String> response = httpClient.send(
                    builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new CodefHttpResponse(response.statusCode(), response.body());
        } catch (HttpTimeoutException exception) {
            // CODEF(특히 개발계)는 카드사 인증 콜백을 기다리느라 응답이 느릴 수 있다. 상류 지연은 503으로 안내한다.
            throw new CodefUnavailableException(
                    "CODEF 응답이 지연되어 처리하지 못했습니다. 잠시 후 다시 시도해주세요.", exception);
        } catch (IOException exception) {
            throw new CodefUnavailableException(
                    "CODEF 연동 서비스에 연결하지 못했습니다. 잠시 후 다시 시도해주세요.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("CODEF 요청이 중단되었습니다.", exception);
        }
    }
}
