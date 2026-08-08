package com.moca.mocabe.domain.benefit.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@DisplayName("카드고릴라 CSV 혜택 상세 원본 검증")
class CardBenefitDetailCoverageTest {

    private static final String RESOURCE_PATH = "/benefit/card-benefit-detail-cases-1206.json";
    private static final String CALCULABLE = "DIRECT_OFFLINE_CALCULABLE";
    private static final String REVIEW_REQUIRED = "DIRECT_OFFLINE_REVIEW_REQUIRED";
    private static final String ONLINE_EXCLUDED = "ONLINE_OR_INDIRECT_EXCLUDED";
    private static final String INFORMATION_ONLY = "INFORMATION_ONLY";
    private static final String NON_RULE_DETAIL = "NON_RULE_DETAIL";

    private final JsonNode root = readFixture();
    private final List<JsonNode> detailCases = readDetailCases(root);

    @Test
    @DisplayName("신용 603건과 체크 603건의 상세 혜택을 빠짐없이 포함한다")
    void containsEveryCsvBenefitDetail() {
        Set<String> cardIds = new HashSet<>();
        long creditCount = countCardType("CREDIT");
        long checkCount = countCardType("CHECK");
        detailCases.forEach(detail -> cardIds.add(text(detail, "cardType") + ":" + text(detail, "cardId")));

        assertEquals(1206, root.path("metadata").path("sourceBenefitCount").asInt());
        assertEquals(1206, root.path("metadata").path("testCaseCount").asInt());
        assertEquals(1206, detailCases.size());
        assertEquals(603, creditCount);
        assertEquals(603, checkCount);
        assertEquals(200, cardIds.size());
        assertTrue(root.path("metadata").path("onlineAndIndirectPaymentsExcluded").asBoolean());
        assertTrue(root.path("metadata").path("detailHtmlExcluded").asBoolean());
    }

    @Test
    @DisplayName("모든 상세 혜택을 계산·검토·제외·정보성 상태로 분류한다")
    void classifiesEveryBenefitDetail() {
        JsonNode modeCounts = root.path("metadata").path("modeCounts");

        assertModeCount(modeCounts, CALCULABLE, 514);
        assertModeCount(modeCounts, REVIEW_REQUIRED, 69);
        assertModeCount(modeCounts, ONLINE_EXCLUDED, 270);
        assertModeCount(modeCounts, INFORMATION_ONLY, 172);
        assertModeCount(modeCounts, NON_RULE_DETAIL, 181);
    }

    @Test
    @DisplayName("모든 상세의 카드·혜택명·원문 해시·출처·분류 사유가 유효하다")
    void validatesEveryBenefitDetailSource() {
        Set<String> allowedModes = Set.of(
                CALCULABLE,
                REVIEW_REQUIRED,
                ONLINE_EXCLUDED,
                INFORMATION_ONLY,
                NON_RULE_DETAIL);

        for (JsonNode detail : detailCases) {
            assertFalse(text(detail, "cardId").isBlank());
            assertFalse(text(detail, "cardName").isBlank());
            assertFalse(text(detail, "benefitTitle").isBlank());
            assertFalse(text(detail, "benefitDescription").isBlank());
            assertEquals(text(detail, "detailTextSha256"), sha256(text(detail, "detailText")));
            assertTrue(detail.path("detailLineCount").asInt() >= 0);
            assertTrue(text(detail, "sourceUrl").startsWith("https://www.card-gorilla.com/card/detail/"));
            assertTrue(allowedModes.contains(text(detail, "mode")));
            assertFalse(text(detail, "classificationReason").isBlank());
        }
    }

    private void assertModeCount(JsonNode modeCounts, String mode, long expectedCount) {
        assertEquals(expectedCount, countMode(mode));
        assertEquals(expectedCount, modeCounts.path(mode).asLong());
    }

    private long countMode(String mode) {
        return detailCases.stream().filter(detail -> mode.equals(text(detail, "mode"))).count();
    }

    private long countCardType(String cardType) {
        return detailCases.stream().filter(detail -> cardType.equals(text(detail, "cardType"))).count();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없다.", exception);
        }
    }

    private String text(JsonNode node, String fieldName) {
        return node.path(fieldName).asText();
    }

    private JsonNode readFixture() {
        try (InputStream input = getClass().getResourceAsStream(RESOURCE_PATH)) {
            assertNotNull(input, RESOURCE_PATH + " 테스트 fixture를 찾을 수 없다.");
            return new ObjectMapper().readTree(input);
        } catch (IOException exception) {
            throw new IllegalStateException("1,206개 혜택 상세 fixture를 읽지 못했다.", exception);
        }
    }

    private List<JsonNode> readDetailCases(JsonNode fixtureRoot) {
        List<JsonNode> cases = new ArrayList<>();
        fixtureRoot.path("benefits").forEach(cases::add);
        return List.copyOf(cases);
    }
}
