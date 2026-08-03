package com.moca.mocabe.domain.codef.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moca.mocabe.domain.codef.model.CodefConnectionCommand;
import com.moca.mocabe.domain.codef.model.CodefOwnedCard;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import javax.crypto.Cipher;

/**
 * CODEF 오픈 API를 호출해 Connected ID를 발급받는 클라이언트다.
 *
 * OAuth 액세스 토큰을 발급받고, password/cardPassword를 CODEF 공개키로 RSA 암호화한 뒤
 * account/create를 호출한다. CODEF 응답은 URL 인코딩된 JSON이므로 디코딩 후 파싱한다.
 */
public class CodefClient {

    private static final String RSA_TRANSFORMATION = "RSA/ECB/PKCS1Padding";
    private static final String LOGIN_TYPE_ID_PASSWORD = "1";
    private static final Logger LOGGER = Logger.getLogger(CodefClient.class.getName());

    private final CodefHttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String clientId;
    private final String clientSecret;
    private final String publicKeyBase64;
    private final String baseUrl;
    private final String tokenUrl;

    public CodefClient(CodefHttpClient httpClient, String clientId, String clientSecret,
                       String publicKeyBase64, String baseUrl, String tokenUrl) {
        this.httpClient = httpClient;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.publicKeyBase64 = publicKeyBase64;
        this.baseUrl = baseUrl;
        this.tokenUrl = tokenUrl;
    }

    public String createConnectedId(CodefConnectionCommand command) {
        // ① 토큰 발급 → ② 요청 조립 → ③ 생성 호출 → ④ 응답 파싱 순서로 진행한다
        String accessToken = requestAccessToken();
        String requestBody = buildRequestBody(command);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + accessToken);
        headers.put("Content-Type", "application/json");
        String responseBody = postSuccessful(baseUrl + "/v1/account/create", headers, requestBody);

        // CODEF는 응답 본문을 URL 인코딩해서 주므로 디코드 후 파싱한다
        JsonNode root = readTree(URLDecoder.decode(responseBody, StandardCharsets.UTF_8));
        String connectedId = root.path("data").path("connectedId").asText("");
        if (connectedId.isBlank()) {
            throw new IllegalStateException("CODEF Connected ID 발급 실패: " + root.path("result"));
        }
        return connectedId;
    }

    /** Connected ID로 개인 보유카드를 조회한다. */
    public List<CodefOwnedCard> getOwnedCards(String connectedId, String organization) {
        String accessToken = requestAccessToken();
        ObjectNode request = objectMapper.createObjectNode();
        request.put("connectedId", connectedId);
        request.put("organization", organization);
        request.put("birthDate", "");
        request.put("inquiryType", "0");

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + accessToken);
        headers.put("Content-Type", "application/json");
        String responseBody = postSuccessful(
                baseUrl + "/v1/kr/card/p/account/card-list", headers, request.toString());
        JsonNode root = readTree(URLDecoder.decode(responseBody, StandardCharsets.UTF_8));
        if (!"CF-00000".equals(root.path("result").path("code").asText())) {
            throw new IllegalStateException("CODEF 보유카드 조회에 실패했습니다.");
        }

        List<CodefOwnedCard> cards = new ArrayList<>();
        JsonNode data = root.path("data");
        if (data.isArray()) {
            for (JsonNode item : data) {
                cards.add(toOwnedCard(item));
            }
        } else if (data.isObject() && data.hasNonNull("resCardName")) {
            // 보유카드가 1장이면 CODEF는 배열이 아닌 단일 객체로 응답한다.
            cards.add(toOwnedCard(data));
        } else {
            LOGGER.warning("CODEF 보유카드 data 형식을 해석할 수 없습니다. result=" + root.path("result")
                    + ", dataType=" + data.getNodeType());
            throw new IllegalStateException("CODEF 보유카드 응답 형식이 올바르지 않습니다.");
        }
        return cards;
    }

    private CodefOwnedCard toOwnedCard(JsonNode item) {
        String cardName = item.path("resCardName").asText("").trim();
        if (cardName.isEmpty()) {
            throw new IllegalStateException("CODEF 보유카드 이름이 누락되었습니다.");
        }
        return new CodefOwnedCard(
                cardName,
                item.path("resCardNo").asText(""),
                item.path("resCardType").asText(""),
                blankToNull(item.path("resImageLink").asText(null)));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String requestAccessToken() {
        // client_credentials 방식으로 Basic 인증해 액세스 토큰을 발급받는다
        String basic = Base64.getEncoder().encodeToString(
                (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Basic " + basic);
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        String body = postSuccessful(tokenUrl, headers, "grant_type=client_credentials&scope=read");
        String accessToken = readTree(body).path("access_token").asText("");
        if (accessToken.isBlank()) {
            throw new IllegalStateException("CODEF 액세스 토큰 발급에 실패했습니다.");
        }
        return accessToken;
    }

    private String postSuccessful(String url, Map<String, String> headers, String body) {
        CodefHttpResponse response = httpClient.post(url, headers, body);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "CODEF HTTP 요청에 실패했습니다. status=" + response.statusCode());
        }
        return response.body();
    }

    private String buildRequestBody(CodefConnectionCommand command) {
        PublicKey publicKey = parsePublicKey();
        ObjectNode account = objectMapper.createObjectNode();
        // 카드·개인 연동만 지원 → 국가/업무/고객 구분은 KR/CD(카드)/P(개인) 고정
        account.put("countryCode", "KR");
        account.put("businessType", "CD");
        account.put("clientType", "P");
        account.put("organization", command.getOrganization());
        account.put("loginType", LOGIN_TYPE_ID_PASSWORD);
        putEncryptedIfPresent(account, "password", command.getPassword(), publicKey);
        putIfPresent(account, "id", command.getId());
        putIfPresent(account, "cardNo", command.getCardNo());
        putEncryptedIfPresent(account, "cardPassword", command.getCardPassword(), publicKey);
        putIfPresent(account, "birthDate", command.getBirthDate());

        ObjectNode body = objectMapper.createObjectNode();
        body.putArray("accountList").add(account);
        return body.toString();
    }

    private void putIfPresent(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value);
        }
    }

    private void putEncryptedIfPresent(ObjectNode node, String field, String value, PublicKey publicKey) {
        if (value != null && !value.isBlank()) {
            node.put(field, rsaEncrypt(value, publicKey));
        }
    }

    private PublicKey parsePublicKey() {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(publicKeyBase64);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("CODEF publicKey 파싱에 실패했습니다.", exception);
        }
    }

    private String rsaEncrypt(String plaintext, PublicKey publicKey) {
        try {
            // CODEF가 요구하는 비밀번호 RSA 암호화(발급 공개키 사용)
            Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            return Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("CODEF 비밀번호 RSA 암호화에 실패했습니다.", exception);
        }
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (IOException exception) {
            throw new IllegalStateException("CODEF 응답 파싱에 실패했습니다.", exception);
        }
    }
}
