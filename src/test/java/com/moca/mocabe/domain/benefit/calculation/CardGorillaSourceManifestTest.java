package com.moca.mocabe.domain.benefit.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@DisplayName("요약 혜택을 제거한 카드고릴라 원본 계약")
class CardGorillaSourceManifestTest {

    private static final String RESOURCE_PATH = "/benefit/card-gorilla-without-summary-manifest.json";
    private final JsonNode root = readManifest();

    @Test
    @DisplayName("신용·체크 각 100개와 상세 혜택 1,198건을 포함한다")
    void containsExpectedCardAndBenefitCounts() {
        JsonNode metadata = root.path("metadata");

        assertEquals(200, metadata.path("sourceCardCount").asInt());
        assertEquals(1198, metadata.path("sourceBenefitCount").asInt());
        assertEquals(100, metadata.path("creditCardCount").asInt());
        assertEquals(100, metadata.path("checkCardCount").asInt());
        assertEquals(5, metadata.path("missingDetailTextCount").asInt());
        assertTrue(metadata.path("summaryBenefitsExcluded").asBoolean());
    }

    @Test
    @DisplayName("카드 ID·순위와 카드별 혜택 해시가 빠짐없이 고정된다")
    void keepsEveryCardIdentityAndBenefitFingerprint() {
        Set<String> cardIds = new HashSet<>();
        Set<String> rankingKeys = new HashSet<>();
        int totalBenefitCount = 0;

        for (JsonNode card : root.path("cards")) {
            String cardId = card.path("cardId").asText();
            String cardType = card.path("cardType").asText();
            int ranking = card.path("ranking").asInt();
            assertFalse(cardId.isBlank());
            assertFalse(card.path("name").asText().isBlank());
            assertTrue(card.path("benefitCount").asInt() > 0);
            assertEquals(64, card.path("benefitsSha256").asText().length());
            assertTrue(cardIds.add(cardId), "중복 카드 ID: " + cardId);
            assertTrue(rankingKeys.add(cardType + ":" + ranking), "중복 카드 순위: " + cardType + ":" + ranking);
            totalBenefitCount += card.path("benefitCount").asInt();
        }

        assertEquals(200, cardIds.size());
        assertEquals(200, rankingKeys.size());
        assertEquals(root.path("metadata").path("sourceBenefitCount").asInt(), totalBenefitCount);
        assertEquals(64, root.path("metadata").path("sourceSha256").asText().length());
    }

    private JsonNode readManifest() {
        try (InputStream input = getClass().getResourceAsStream(RESOURCE_PATH)) {
            assertNotNull(input, RESOURCE_PATH + " fixture를 찾을 수 없습니다.");
            return new ObjectMapper().readTree(input);
        } catch (IOException exception) {
            throw new IllegalStateException("카드고릴라 원본 manifest를 읽지 못했습니다.", exception);
        }
    }
}
