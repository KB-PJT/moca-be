package com.moca.mocabe.global.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@SuppressWarnings("unchecked")
class GoogleAuthorizationCodeClientTest {

    @Test
    @DisplayName("authorization code를 서버에서 교환하고 client ID, 만료, scope, user_id를 검증한다")
    void exchangesAndVerifiesGoogleAccessToken() {
        GoogleOAuthHttpClient httpClient = org.mockito.Mockito.mock(GoogleOAuthHttpClient.class);
        GoogleAuthorizationCodeClient client = client(httpClient);
        when(httpClient.postForm(anyString(), org.mockito.ArgumentMatchers.<String, String>anyMap()))
                .thenReturn(new GoogleOAuthHttpResponse(200, "{\"access_token\":\"google-access-token\"}"));
        when(httpClient.get(anyString())).thenReturn(new GoogleOAuthHttpResponse(200,
                "{\"audience\":\"google-client-id\",\"expires_in\":3600,"
                        + "\"scope\":\"openid https://www.googleapis.com/auth/userinfo.email\","
                        + "\"user_id\":\"google-user-id\",\"email\":\"moca@example.com\"}"));

        GoogleUserIdentity identity = client.exchangeAndVerify("authorization-code", "code-verifier",
                "https://moca.example.com/auth/callback");

        assertEquals("google-user-id", identity.getSubject());
        assertEquals("moca@example.com", identity.getEmail());
        ArgumentCaptor<Map<String, String>> form = ArgumentCaptor.forClass(Map.class);
        verify(httpClient).postForm(org.mockito.ArgumentMatchers.eq("https://oauth2.googleapis.com/token"),
                form.capture());
        assertEquals("google-client-secret", form.getValue().get("client_secret"));
        assertEquals("code-verifier", form.getValue().get("code_verifier"));
        assertEquals("https://moca.example.com/auth/callback", form.getValue().get("redirect_uri"));
        verify(httpClient).get("https://www.googleapis.com/oauth2/v2/tokeninfo?access_token=google-access-token");
    }

    @Test
    @DisplayName("다른 client ID, 만료, 누락 scope 또는 user_id의 tokeninfo 응답은 거절한다")
    void rejectsInvalidTokenInfo() {
        assertInvalidTokenInfo("{\"audience\":\"other-client\",\"expires_in\":3600,"
                + "\"scope\":\"openid https://www.googleapis.com/auth/userinfo.email\",\"user_id\":\"user\"}");
        assertInvalidTokenInfo("{\"audience\":\"google-client-id\",\"expires_in\":0,"
                + "\"scope\":\"openid https://www.googleapis.com/auth/userinfo.email\",\"user_id\":\"user\"}");
        assertInvalidTokenInfo("{\"audience\":\"google-client-id\",\"expires_in\":3600,"
                + "\"scope\":\"openid\",\"user_id\":\"user\"}");
        assertInvalidTokenInfo("{\"audience\":\"google-client-id\",\"expires_in\":3600,"
                + "\"scope\":\"openid https://www.googleapis.com/auth/userinfo.email\"}");
    }

    @Test
    @DisplayName("허용 목록에 없는 redirect URI는 Google token 교환 전에 거절한다")
    void rejectsRedirectUriOutsideAllowlist() {
        GoogleOAuthHttpClient httpClient = org.mockito.Mockito.mock(GoogleOAuthHttpClient.class);
        GoogleAuthorizationCodeClient client = client(httpClient);

        assertThrows(GoogleAuthorizationCodeException.class,
                () -> client.exchangeAndVerify("code", "verifier", "https://untrusted.example.com/callback"));
        org.mockito.Mockito.verifyNoInteractions(httpClient);
    }

    @Test
    @DisplayName("Google token 교환 또는 tokeninfo가 실패하면 로그인을 거절한다")
    void rejectsFailedGoogleResponses() {
        GoogleOAuthHttpClient httpClient = org.mockito.Mockito.mock(GoogleOAuthHttpClient.class);
        GoogleAuthorizationCodeClient client = client(httpClient);
        when(httpClient.postForm(anyString(), org.mockito.ArgumentMatchers.<String, String>anyMap()))
                .thenReturn(new GoogleOAuthHttpResponse(400, "{\"error\":\"invalid_grant\"}"));

        assertThrows(GoogleAuthorizationCodeException.class,
                () -> client.exchangeAndVerify("code", "verifier", "https://moca.example.com/auth/callback"));
    }

    @Test
    @DisplayName("tokeninfo 실패 또는 잘못된 JSON은 로그인을 거절한다")
    void rejectsFailedOrMalformedTokenInfoResponse() {
        GoogleOAuthHttpClient httpClient = org.mockito.Mockito.mock(GoogleOAuthHttpClient.class);
        GoogleAuthorizationCodeClient client = client(httpClient);
        when(httpClient.postForm(anyString(), org.mockito.ArgumentMatchers.<String, String>anyMap()))
                .thenReturn(new GoogleOAuthHttpResponse(200, "{\"access_token\":\"access\"}"));
        when(httpClient.get(anyString())).thenReturn(new GoogleOAuthHttpResponse(401, "{}"));

        assertThrows(GoogleAuthorizationCodeException.class,
                () -> client.exchangeAndVerify("code", "verifier", "https://moca.example.com/auth/callback"));
        when(httpClient.get(anyString())).thenReturn(new GoogleOAuthHttpResponse(200, "not-json"));
        assertThrows(GoogleAuthorizationCodeException.class,
                () -> client.exchangeAndVerify("code", "verifier", "https://moca.example.com/auth/callback"));
    }

    @Test
    @DisplayName("Google client ID와 secret은 비어 있으면 안 된다")
    void rejectsMissingGoogleClientConfiguration() {
        GoogleOAuthHttpClient httpClient = org.mockito.Mockito.mock(GoogleOAuthHttpClient.class);

        assertThrows(IllegalArgumentException.class,
                () -> new GoogleAuthorizationCodeClient(httpClient, " ", "secret", List.of("redirect"), List.of(),
                        new ObjectMapper()));
        assertThrows(IllegalArgumentException.class,
                () -> new GoogleAuthorizationCodeClient(httpClient, "client", " ", List.of("redirect"), List.of(),
                        new ObjectMapper()));
        assertThrows(IllegalArgumentException.class,
                () -> new GoogleAuthorizationCodeClient(httpClient, "client", "secret", List.of(" "), List.of(),
                        new ObjectMapper()));
    }

    private void assertInvalidTokenInfo(String tokenInfo) {
        GoogleOAuthHttpClient httpClient = org.mockito.Mockito.mock(GoogleOAuthHttpClient.class);
        GoogleAuthorizationCodeClient client = client(httpClient);
        when(httpClient.postForm(anyString(), org.mockito.ArgumentMatchers.<String, String>anyMap()))
                .thenReturn(new GoogleOAuthHttpResponse(200, "{\"access_token\":\"access\"}"));
        when(httpClient.get(anyString())).thenReturn(new GoogleOAuthHttpResponse(200, tokenInfo));

        assertThrows(GoogleAuthorizationCodeException.class,
                () -> client.exchangeAndVerify("code", "verifier", "https://moca.example.com/auth/callback"));
    }

    private GoogleAuthorizationCodeClient client(GoogleOAuthHttpClient httpClient) {
        return new GoogleAuthorizationCodeClient(httpClient, "google-client-id", "google-client-secret",
                List.of("http://localhost:5173/auth/callback", "https://moca.example.com/auth/callback"),
                List.of("openid", "https://www.googleapis.com/auth/userinfo.email"), new ObjectMapper());
    }
}
