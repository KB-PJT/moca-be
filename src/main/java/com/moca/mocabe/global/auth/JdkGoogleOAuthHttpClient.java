package com.moca.mocabe.global.auth;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** JDK HttpClient로 Google OAuth 서버 간 요청을 전송한다. */
public class JdkGoogleOAuthHttpClient implements GoogleOAuthHttpClient {

    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public JdkGoogleOAuthHttpClient(HttpClient httpClient, Duration requestTimeout) {
        this.httpClient = Objects.requireNonNull(httpClient);
        this.requestTimeout = Objects.requireNonNull(requestTimeout);
    }

    @Override
    public GoogleOAuthHttpResponse postForm(String url, Map<String, String> form) {
        String body = form.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(requestTimeout)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return send(request);
    }

    @Override
    public GoogleOAuthHttpResponse get(String url) {
        return get(url, Collections.emptyMap());
    }

    @Override
    public GoogleOAuthHttpResponse get(String url, Map<String, String> headers) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(url))
                .timeout(requestTimeout)
                .GET();
        headers.forEach(requestBuilder::header);
        HttpRequest request = requestBuilder.build();
        return send(request);
    }

    private GoogleOAuthHttpResponse send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new GoogleOAuthHttpResponse(response.statusCode(), response.body());
        } catch (IOException exception) {
            throw new IllegalStateException("Google OAuth 요청에 실패했습니다.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Google OAuth 요청이 중단되었습니다.", exception);
        }
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
