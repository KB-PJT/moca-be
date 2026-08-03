package com.moca.mocabe.domain.codef.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.moca.mocabe.domain.codef.model.CodefConnectionCommand;
import com.moca.mocabe.domain.codef.model.CodefOwnedCard;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
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
    private static final String CARD_LIST_URL = BASE_URL + "/v1/kr/card/p/account/card-list";
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
        when(httpClient.post(eq(TOKEN_URL), any(), anyString())).thenReturn(ok(TOKEN_RESPONSE));
        when(httpClient.post(eq(CREATE_URL), any(), anyString())).thenReturn(ok(urlEncoded(
                "{\"result\":{\"code\":\"CF-00000\"},\"data\":{\"connectedId\":\"cid-xyz\"}}")));

        String connectedId = codefClient.createConnectedId(command("pw", "1234567890", "1234", "900101"));

        assertEquals("cid-xyz", connectedId);
    }

    @Test
    @DisplayName("HTTP는 200이어도 result.code가 실패면 connectedId가 있어도 예외를 던진다")
    void throwsWhenResultCodeIsFailureEvenIfConnectedIdPresent() {
        when(httpClient.post(eq(TOKEN_URL), any(), anyString())).thenReturn(ok(TOKEN_RESPONSE));
        when(httpClient.post(eq(CREATE_URL), any(), anyString())).thenReturn(ok(urlEncoded(
                "{\"result\":{\"code\":\"CF-94002\",\"message\":\"실패\"},"
                        + "\"data\":{\"connectedId\":\"cid-xyz\"}}")));

        assertThrows(IllegalStateException.class,
                () -> codefClient.createConnectedId(command("pw", null, null, null)));
    }

    @Test
    @DisplayName("result.code가 실패면 응답에 connectedId가 없어도 그 자체로 예외를 던진다")
    void throwsWhenConnectedIdMissing() {
        when(httpClient.post(eq(TOKEN_URL), any(), anyString())).thenReturn(ok(TOKEN_RESPONSE));
        when(httpClient.post(eq(CREATE_URL), any(), anyString())).thenReturn(ok(urlEncoded(
                "{\"result\":{\"code\":\"CF-12345\",\"message\":\"실패\"}}")));

        assertThrows(IllegalStateException.class,
                () -> codefClient.createConnectedId(command("pw", null, null, null)));
    }

    @Test
    @DisplayName("result.code는 성공인데 connectedId가 비어 있으면 예외를 던진다")
    void throwsWhenSuccessResultHasBlankConnectedId() {
        when(httpClient.post(eq(TOKEN_URL), any(), anyString())).thenReturn(ok(TOKEN_RESPONSE));
        when(httpClient.post(eq(CREATE_URL), any(), anyString())).thenReturn(ok(urlEncoded(
                "{\"result\":{\"code\":\"CF-00000\"},\"data\":{}}")));

        assertThrows(IllegalStateException.class,
                () -> codefClient.createConnectedId(command("pw", null, null, null)));
    }

    @Test
    @DisplayName("응답 JSON이 손상되면 예외를 던진다")
    void throwsWhenResponseMalformed() {
        when(httpClient.post(eq(TOKEN_URL), any(), anyString())).thenReturn(ok(TOKEN_RESPONSE));
        when(httpClient.post(eq(CREATE_URL), any(), anyString())).thenReturn(ok(urlEncoded("not-json")));

        assertThrows(IllegalStateException.class,
                () -> codefClient.createConnectedId(command("pw", null, null, null)));
    }

    @Test
    @DisplayName("publicKey가 잘못되면 예외를 던진다")
    void throwsWhenPublicKeyInvalid() {
        CodefClient invalidKeyClient = new CodefClient(httpClient, "client-id", "client-secret",
                "!!!not-base64!!!", BASE_URL, TOKEN_URL);
        when(httpClient.post(eq(TOKEN_URL), any(), anyString())).thenReturn(ok(TOKEN_RESPONSE));

        assertThrows(IllegalStateException.class,
                () -> invalidKeyClient.createConnectedId(command("pw", null, null, null)));
    }

    @Test
    @DisplayName("RSA 한도를 넘는 비밀번호는 암호화 실패로 예외를 던진다")
    void throwsWhenRsaEncryptionFails() {
        when(httpClient.post(eq(TOKEN_URL), any(), anyString())).thenReturn(ok(TOKEN_RESPONSE));
        String tooLongPassword = "a".repeat(500);

        assertThrows(IllegalStateException.class,
                () -> codefClient.createConnectedId(command(tooLongPassword, null, null, null)));
    }

    @Test
    @DisplayName("액세스 토큰이 비어 있으면 토큰 발급 단계에서 실패한다")
    void throwsWhenAccessTokenMissing() {
        when(httpClient.post(eq(TOKEN_URL), any(), anyString()))
                .thenReturn(ok("{\"token_type\":\"bearer\"}"));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> codefClient.createConnectedId(command("pw", null, null, null)));

        assertEquals("CODEF 액세스 토큰 발급에 실패했습니다.", exception.getMessage());
    }

    @Test
    @DisplayName("CODEF가 비 2xx 상태를 반환하면 상태 코드를 포함해 실패한다")
    void throwsWhenHttpStatusIsNotSuccessful() {
        when(httpClient.post(eq(TOKEN_URL), any(), anyString()))
                .thenReturn(new CodefHttpResponse(401, "{\"error\":\"unauthorized\"}"));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> codefClient.createConnectedId(command("pw", null, null, null)));

        assertEquals("CODEF HTTP 요청에 실패했습니다. status=401", exception.getMessage());
    }

    @Test
    @DisplayName("보유카드 이름·번호·종류·이미지를 내부 모델로 변환하고 빈 이미지는 null로 만든다")
    void returnsOwnedCards() {
        when(httpClient.post(eq(TOKEN_URL), any(), anyString())).thenReturn(ok(TOKEN_RESPONSE));
        when(httpClient.post(eq(CARD_LIST_URL), any(), anyString())).thenReturn(ok(urlEncoded(
                "{\"result\":{\"code\":\"CF-00000\"},\"data\":["
                        + "{\"resCardName\":\"카드 A\",\"resCardNo\":\"1234****5678\","
                        + "\"resCardType\":\"신용/본인\",\"resImageLink\":\"https://codef/a.png\"},"
                        + "{\"resCardName\":\"카드 B\",\"resImageLink\":\"\"}]}")));

        List<CodefOwnedCard> cards = codefClient.getOwnedCards("cid-1", "0301");

        assertEquals(2, cards.size());
        assertEquals("카드 A", cards.get(0).cardName());
        assertEquals("https://codef/a.png", cards.get(0).imageUrl());
        assertEquals(null, cards.get(1).imageUrl());
    }

    @Test
    @DisplayName("보유카드가 1장이면 data가 단일 객체여도 한 장으로 파싱한다")
    void returnsSingleOwnedCardFromObject() {
        when(httpClient.post(eq(TOKEN_URL), any(), anyString())).thenReturn(ok(TOKEN_RESPONSE));
        when(httpClient.post(eq(CARD_LIST_URL), any(), anyString())).thenReturn(ok(urlEncoded(
                "{\"result\":{\"code\":\"CF-00000\"},\"data\":{"
                        + "\"resCardName\":\"노리2 체크카드(KB Pay)_비교통\",\"resCardNo\":\"943646******1069\","
                        + "\"resCardType\":\"\",\"resImageLink\":\"\"}}")));

        List<CodefOwnedCard> cards = codefClient.getOwnedCards("cid-1", "0301");

        assertEquals(1, cards.size());
        assertEquals("노리2 체크카드(KB Pay)_비교통", cards.get(0).cardName());
        assertEquals(null, cards.get(0).imageUrl());
    }

    @Test
    @DisplayName("보유카드 상품 결과 코드가 실패면 예외를 던진다")
    void rejectsFailedOwnedCardResult() {
        when(httpClient.post(eq(TOKEN_URL), any(), anyString())).thenReturn(ok(TOKEN_RESPONSE));
        when(httpClient.post(eq(CARD_LIST_URL), any(), anyString())).thenReturn(ok(urlEncoded(
                "{\"result\":{\"code\":\"CF-12345\"},\"data\":[]}")));

        assertThrows(IllegalStateException.class,
                () -> codefClient.getOwnedCards("cid-1", "0301"));
    }

    @Test
    @DisplayName("보유카드 data가 배열이 아니면 예외를 던진다")
    void rejectsInvalidOwnedCardData() {
        when(httpClient.post(eq(TOKEN_URL), any(), anyString())).thenReturn(ok(TOKEN_RESPONSE));
        when(httpClient.post(eq(CARD_LIST_URL), any(), anyString())).thenReturn(ok(urlEncoded(
                "{\"result\":{\"code\":\"CF-00000\"},\"data\":{}}")));

        assertThrows(IllegalStateException.class,
                () -> codefClient.getOwnedCards("cid-1", "0301"));
    }

    @Test
    @DisplayName("보유카드 이름이 없으면 후보를 만들지 않고 예외를 던진다")
    void rejectsOwnedCardWithoutName() {
        when(httpClient.post(eq(TOKEN_URL), any(), anyString())).thenReturn(ok(TOKEN_RESPONSE));
        when(httpClient.post(eq(CARD_LIST_URL), any(), anyString())).thenReturn(ok(urlEncoded(
                "{\"result\":{\"code\":\"CF-00000\"},\"data\":[{\"resCardName\":\" \"}]}")));

        assertThrows(IllegalStateException.class,
                () -> codefClient.getOwnedCards("cid-1", "0301"));
    }

    private CodefConnectionCommand command(String password, String cardNo, String cardPassword,
                                           String birthDate) {
        return new CodefConnectionCommand("0301", "tester", password, cardNo, cardPassword, birthDate);
    }

    private String urlEncoded(String json) {
        return URLEncoder.encode(json, StandardCharsets.UTF_8);
    }

    private CodefHttpResponse ok(String body) {
        return new CodefHttpResponse(200, body);
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
