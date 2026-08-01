package com.moca.mocabe.domain.codef.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.moca.mocabe.domain.codef.model.CodefConnectionCommand;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CodefClientTest {

    private static final String TOKEN_URL = "https://oauth.example.com/token";
    private static final String BASE_URL = "https://api.example.com";
    private static final String CREATE_URL = BASE_URL + "/v1/account/create";
    private static final String TOKEN_RESPONSE = "{\"access_token\":\"tok-1\"}";
    private static final String PUBLIC_KEY_BASE64 = generatePublicKey();

    @Mock
    private CodefHttpClient httpClient;

    private CodefClient codefClient;

    @BeforeEach
    void setUp() {
        codefClient = new CodefClient(httpClient, "client-id", "client-secret",
                PUBLIC_KEY_BASE64, BASE_URL, TOKEN_URL);
    }

    @Test
    @DisplayName("토큰 발급·RSA 암호화·요청을 거쳐 connectedId를 반환한다")
    void returnsConnectedId() {
        when(httpClient.post(eq(TOKEN_URL), any(), anyString())).thenReturn(TOKEN_RESPONSE);
        when(httpClient.post(eq(CREATE_URL), any(), anyString())).thenReturn(urlEncoded(
                "{\"result\":{\"code\":\"CF-00000\"},\"data\":{\"connectedId\":\"cid-xyz\"}}"));

        String connectedId = codefClient.createConnectedId(command("pw", "1234567890", "1234", "900101"));

        assertEquals("cid-xyz", connectedId);
    }

    @Test
    @DisplayName("응답에 connectedId가 없으면 예외를 던진다")
    void throwsWhenConnectedIdMissing() {
        when(httpClient.post(eq(TOKEN_URL), any(), anyString())).thenReturn(TOKEN_RESPONSE);
        when(httpClient.post(eq(CREATE_URL), any(), anyString())).thenReturn(urlEncoded(
                "{\"result\":{\"code\":\"CF-12345\",\"message\":\"실패\"}}"));

        assertThrows(IllegalStateException.class,
                () -> codefClient.createConnectedId(command("pw", null, null, null)));
    }

    @Test
    @DisplayName("응답 JSON이 손상되면 예외를 던진다")
    void throwsWhenResponseMalformed() {
        when(httpClient.post(eq(TOKEN_URL), any(), anyString())).thenReturn(TOKEN_RESPONSE);
        when(httpClient.post(eq(CREATE_URL), any(), anyString())).thenReturn(urlEncoded("not-json"));

        assertThrows(IllegalStateException.class,
                () -> codefClient.createConnectedId(command("pw", null, null, null)));
    }

    @Test
    @DisplayName("publicKey가 잘못되면 예외를 던진다")
    void throwsWhenPublicKeyInvalid() {
        CodefClient invalidKeyClient = new CodefClient(httpClient, "client-id", "client-secret",
                "!!!not-base64!!!", BASE_URL, TOKEN_URL);
        when(httpClient.post(eq(TOKEN_URL), any(), anyString())).thenReturn(TOKEN_RESPONSE);

        assertThrows(IllegalStateException.class,
                () -> invalidKeyClient.createConnectedId(command("pw", null, null, null)));
    }

    @Test
    @DisplayName("RSA 한도를 넘는 비밀번호는 암호화 실패로 예외를 던진다")
    void throwsWhenRsaEncryptionFails() {
        when(httpClient.post(eq(TOKEN_URL), any(), anyString())).thenReturn(TOKEN_RESPONSE);
        String tooLongPassword = "a".repeat(500);

        assertThrows(IllegalStateException.class,
                () -> codefClient.createConnectedId(command(tooLongPassword, null, null, null)));
    }

    private CodefConnectionCommand command(String password, String cardNo, String cardPassword,
                                           String birthDate) {
        return new CodefConnectionCommand("0301", "tester", password, cardNo, cardPassword, birthDate);
    }

    private String urlEncoded(String json) {
        return URLEncoder.encode(json, StandardCharsets.UTF_8);
    }

    private static String generatePublicKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return Base64.getEncoder().encodeToString(generator.generateKeyPair().getPublic().getEncoded());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
