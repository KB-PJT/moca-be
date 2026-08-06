package com.moca.mocabe.domain.codef.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moca.mocabe.domain.codef.exception.CodefInvalidCredentialsException;
import com.moca.mocabe.domain.codef.exception.CodefUnavailableException;
import com.moca.mocabe.domain.codef.model.CodefApproval;
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
    private static final String RESULT_CODE_SUCCESS = "CF-00000";
    private static final String ERROR_CODE_INVALID_CREDENTIALS = "CF-12803";
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
        // HTTP 200이어도 result.code로 실패를 반환할 수 있어 connectedId 유무보다 먼저 확인한다.
        if (!RESULT_CODE_SUCCESS.equals(root.path("result").path("code").asText())) {
            // result.code(예 CF-04000)는 "계정 등록 실패"라는 요약일 뿐 원인은 data.errorList[].code에
            // 있다. 그중 아이디/비밀번호 오류(CF-12803)는 CODEF 상류 문제가 아니라 사용자 입력 오류이므로
            // 재시도 안내(503)가 아니라 400으로 구분해 알려준다. 그 외는 CODEF 상류 문제로 본다.
            if (hasErrorCode(root.path("data").path("errorList"), ERROR_CODE_INVALID_CREDENTIALS)) {
                throw new CodefInvalidCredentialsException();
            }
            // 사용자 입력 오류로 특정하지 못한 나머지는 원인 진단을 위해 CODEF result 코드를 로그로 남긴다.
            logResultFailure("Connected ID 발급", root);
            throw new CodefUnavailableException(
                    "CODEF Connected ID 발급에 실패했습니다. 잠시 후 다시 시도해주세요.");
        }
        String connectedId = root.path("data").path("connectedId").asText("");
        if (connectedId.isBlank()) {
            throw new IllegalStateException("CODEF Connected ID 발급 실패: " + root.path("result"));
        }
        return connectedId;
    }

    /** Connected ID로 개인 보유카드를 조회한다. */
    public List<CodefOwnedCard> getOwnedCards(String connectedId, String organization,
                                               String cardNo, String cardPassword, String birthDate) {
        String accessToken = requestAccessToken();
        ObjectNode request = objectMapper.createObjectNode();
        request.put("connectedId", connectedId);
        request.put("organization", organization);
        // TODO(BE): 현대카드(0302) 보유카드 조회에서 확인된 요구값이다. 카드사별 필수값과
        // cardPassword RSA 암호화 규칙을 CODEF 명세 기준으로 최종 확인해 유지·조정한다.
        putIfPresent(request, "cardNo", cardNo);
        putEncryptedIfPresent(request, "cardPassword", cardPassword, parsePublicKey());
        request.put("birthDate", birthDate == null ? "" : birthDate);
        request.put("inquiryType", "0");

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + accessToken);
        headers.put("Content-Type", "application/json");
        String responseBody = postSuccessful(
                baseUrl + "/v1/kr/card/p/account/card-list", headers, request.toString());
        JsonNode root = readTree(URLDecoder.decode(responseBody, StandardCharsets.UTF_8));
        if (!RESULT_CODE_SUCCESS.equals(root.path("result").path("code").asText())) {
            // CODEF 상류 문제이므로 500이 아니라 재시도 가능한 503으로 안내하고, 원인 진단을 위해 로그를 남긴다.
            logResultFailure("보유카드 조회", root);
            throw new CodefUnavailableException("CODEF 보유카드 조회에 실패했습니다. 잠시 후 다시 시도해주세요.");
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

    /**
     * Connected ID로 개인 카드 승인내역(거래내역)을 조회한다.
     *
     * inquiryType="1"(전체조회)로 카드사 전체 카드의 승인내역을 한 번에 받아 카드 매칭은 호출자가 수행한다.
     * 가맹점 상세는 요청하지 않으며(memberStoreInfoType="0"), 날짜는 CODEF 규격인 YYYYMMDD로 전달한다.
     */
    public List<CodefApproval> getApprovals(String connectedId, String organization,
                                            String birthDate, String startDate, String endDate) {
        String accessToken = requestAccessToken();
        ObjectNode request = objectMapper.createObjectNode();
        request.put("connectedId", connectedId);
        request.put("organization", organization);
        request.put("birthDate", birthDate == null ? "" : birthDate);
        request.put("startDate", startDate);
        request.put("endDate", endDate);
        request.put("orderBy", "0");
        request.put("inquiryType", "1");
        request.put("memberStoreInfoType", "0");

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + accessToken);
        headers.put("Content-Type", "application/json");
        String responseBody = postSuccessful(
                baseUrl + "/v1/kr/card/p/account/approval-list", headers, request.toString());
        JsonNode root = readTree(URLDecoder.decode(responseBody, StandardCharsets.UTF_8));
        if (!RESULT_CODE_SUCCESS.equals(root.path("result").path("code").asText())) {
            // CODEF 상류 문제이므로 500이 아니라 재시도 가능한 503으로 안내하고, 원인 진단을 위해 로그를 남긴다.
            logResultFailure("승인내역 조회", root);
            throw new CodefUnavailableException("CODEF 승인내역 조회에 실패했습니다. 잠시 후 다시 시도해주세요.");
        }

        List<CodefApproval> approvals = new ArrayList<>();
        JsonNode data = root.path("data");
        // 승인내역이 0건으로 보일 때 CODEF 응답 형태(배열/객체/필드)를 바로 확인할 수 있도록 남긴다(FINE).
        LOGGER.fine("CODEF 승인내역 응답 dataType=" + data.getNodeType()
                + ", size=" + data.size() + ", fields=" + fieldNames(data));
        if (data.isArray()) {
            for (JsonNode item : data) {
                approvals.add(toApproval(item));
            }
        } else if (data.isObject() && data.hasNonNull("resUsedDate")) {
            // 승인내역이 1건이면 CODEF는 배열이 아닌 단일 객체로 응답한다.
            approvals.add(toApproval(data));
        } else if (data.isObject() && data.size() == 0) {
            // 조회 기간에 승인내역이 없으면 빈 객체({})로 응답할 수 있다.
            return approvals;
        } else {
            LOGGER.warning("CODEF 승인내역 data 형식을 해석할 수 없습니다. result=" + root.path("result")
                    + ", dataType=" + data.getNodeType() + ", fields=" + fieldNames(data));
            throw new IllegalStateException("CODEF 승인내역 응답 형식이 올바르지 않습니다.");
        }
        return approvals;
    }

    /** CODEF result 코드/메시지(값은 고정 안내문이라 민감정보 없음)를 남겨 어떤 CF-코드로 실패했는지 진단할 수 있게 한다. */
    private void logResultFailure(String operation, JsonNode root) {
        LOGGER.warning("CODEF " + operation + " 실패 code=" + root.path("result").path("code").asText()
                + " message=" + root.path("result").path("message").asText());
    }

    /** errorList에 지정한 코드가 있는지 확인한다. 계정 1건만 요청해도 CODEF 관례상 배열/단일객체 둘 다 올 수 있다. */
    private boolean hasErrorCode(JsonNode errorListNode, String code) {
        for (JsonNode error : asNodeList(errorListNode)) {
            if (code.equals(error.path("code").asText())) {
                return true;
            }
        }
        return false;
    }

    /** CODEF는 항목이 1개면 배열 대신 단일 객체로 응답하는 경우가 있어 배열/단일객체/없음을 모두 흡수한다. */
    private List<JsonNode> asNodeList(JsonNode node) {
        List<JsonNode> nodes = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(nodes::add);
        } else if (node.isObject() && node.size() > 0) {
            nodes.add(node);
        }
        return nodes;
    }

    /** 진단 로그용으로 객체 노드의 필드명 목록을 만든다(값은 남기지 않아 민감정보 노출을 피한다). */
    private String fieldNames(JsonNode node) {
        if (node == null || !node.isObject()) {
            return "[]";
        }
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names.toString();
    }

    private CodefApproval toApproval(JsonNode item) {
        return new CodefApproval(
                item.path("resUsedDate").asText(""),
                item.path("resUsedTime").asText(""),
                item.path("resCardNo").asText(""),
                item.path("resCardName").asText("").trim(),
                item.path("resMemberStoreName").asText(""),
                item.path("resUsedAmount").asText(""),
                blankToNull(item.path("resApprovalNo").asText("")),
                item.path("resHomeForeignType").asText(""),
                item.path("resCancelYN").asText(""),
                item.toString());
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
        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            // 404·401·5xx 등 CODEF가 준 비2xx 상태는 우리 쪽 500이 아니라 재시도 가능한 503으로 안내한다.
            // (URLTimeout/연결 실패는 JdkCodefHttpClient에서 이미 같은 예외로 변환한다.)
            throw new CodefUnavailableException(
                    "CODEF 요청이 실패했습니다(HTTP " + statusCode + "). 잠시 후 다시 시도해주세요.");
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
