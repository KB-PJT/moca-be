package com.moca.mocabe.global.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Google PKCE authorization code를 access token으로 교환하고 tokeninfo 응답을 검증한다. */
public class GoogleAuthorizationCodeClient implements GoogleAuthorizationCodeExchanger {

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String TOKEN_INFO_URL = "https://www.googleapis.com/oauth2/v2/tokeninfo";

    private final GoogleOAuthHttpClient httpClient;
    private final String clientId;
    private final String clientSecret;
    private final Set<String> allowedRedirectUris;
    private final Set<String> requiredScopes;
    private final ObjectMapper objectMapper;

    public GoogleAuthorizationCodeClient(GoogleOAuthHttpClient httpClient, String clientId, String clientSecret,
                                         List<String> allowedRedirectUris, List<String> requiredScopes,
                                         ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.clientId = requireValue(clientId, "MOCA_GOOGLE_CLIENT_ID");
        this.clientSecret = requireValue(clientSecret, "MOCA_GOOGLE_CLIENT_SECRET");
        this.allowedRedirectUris = allowedRedirectUris.stream().map(String::trim).filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        if (this.allowedRedirectUris.isEmpty()) {
            throw new IllegalArgumentException("MOCA_GOOGLE_ALLOWED_REDIRECT_URIS는 필수입니다.");
        }
        this.requiredScopes = requiredScopes.stream().map(String::trim).filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.objectMapper = objectMapper;
    }

    @Override
    public GoogleUserIdentity exchangeAndVerify(String code, String codeVerifier, String redirectUri) {
        if (!allowedRedirectUris.contains(redirectUri)) {
            throw new GoogleAuthorizationCodeException();
        }
        GoogleOAuthHttpResponse tokenResponse = httpClient.postForm(TOKEN_URL,
                tokenExchangeForm(code, codeVerifier, redirectUri));
        if (!isSuccess(tokenResponse)) {
            throw new GoogleAuthorizationCodeException();
        }
        String accessToken = requiredText(readTree(tokenResponse), "access_token");
        GoogleOAuthHttpResponse tokenInfoResponse = httpClient.get(
                TOKEN_INFO_URL + "?access_token=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8));
        if (!isSuccess(tokenInfoResponse)) {
            throw new GoogleAuthorizationCodeException();
        }
        return verifiedIdentity(readTree(tokenInfoResponse));
    }

    private Map<String, String> tokenExchangeForm(String code, String codeVerifier, String redirectUri) {
        Map<String, String> form = new LinkedHashMap<String, String>();
        form.put("client_id", clientId);
        form.put("client_secret", clientSecret);
        form.put("code", code);
        form.put("code_verifier", codeVerifier);
        form.put("redirect_uri", redirectUri);
        form.put("grant_type", "authorization_code");
        return form;
    }

    private GoogleUserIdentity verifiedIdentity(JsonNode tokenInfo) {
        if (!clientId.equals(tokenInfo.path("audience").asText()) || tokenInfo.path("expires_in").asLong(0) <= 0
                || !grantedScopes(tokenInfo).containsAll(requiredScopes)) {
            throw new GoogleAuthorizationCodeException();
        }
        return new GoogleUserIdentity(requiredText(tokenInfo, "user_id"), tokenInfo.path("email").asText(null));
    }

    private Set<String> grantedScopes(JsonNode tokenInfo) {
        return java.util.Arrays.stream(tokenInfo.path("scope").asText().split("\\s+"))
                .filter(value -> !value.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    private JsonNode readTree(GoogleOAuthHttpResponse response) {
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception exception) {
            throw new GoogleAuthorizationCodeException();
        }
    }

    private String requiredText(JsonNode node, String fieldName) {
        String value = node.path(fieldName).asText();
        if (value.trim().isEmpty()) {
            throw new GoogleAuthorizationCodeException();
        }
        return value;
    }

    private boolean isSuccess(GoogleOAuthHttpResponse response) {
        return response.getStatusCode() >= 200 && response.getStatusCode() < 300;
    }

    private String requireValue(String value, String propertyName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(propertyName + "는 필수입니다.");
        }
        return value.trim();
    }
}
