package com.moca.mocabe.domain.card.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moca.mocabe.domain.card.dto.MeCardItemResponse;
import com.moca.mocabe.domain.card.dto.MeCardsResponse;
import com.moca.mocabe.domain.card.model.UserCardListRow;
import com.moca.mocabe.domain.card.service.CardQueryService;
import com.moca.mocabe.global.auth.CurrentUserProvider;
import com.moca.mocabe.global.exception.GlobalExceptionHandler;
import com.moca.mocabe.global.exception.auth.AuthenticationRequiredException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MeCardControllerTest {

    private static final String USER_ID = "01980d6a-5c0c-7aaf-9b85-010203040506";
    private static final String ACTIVE_USER_CARD_ID = "01980d6a-5c0c-7aaf-9b85-010203040531";
    private static final String INACTIVE_USER_CARD_ID = "01980d6a-5c0c-7aaf-9b85-010203040533";
    private static final String ISSUER_ID = "00000000-0000-4000-8000-000000000301";

    private CardQueryService cardQueryService;
    private CurrentUserProvider currentUserProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        cardQueryService = org.mockito.Mockito.mock(CardQueryService.class);
        currentUserProvider = org.mockito.Mockito.mock(CurrentUserProvider.class);
        MeCardController controller = new MeCardController(cardQueryService, currentUserProvider);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    @Test
    @DisplayName("쿼리를 생략하면 활성 카드와 비활성 카드를 data로 반환한다")
    void returnsActiveAndInactiveCardsByDefault() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(cardQueryService.getMyCards(USER_ID, false)).thenReturn(new MeCardsResponse(
                null,
                List.of(card(ACTIVE_USER_CARD_ID, "KB My WE:SH",
                        "https://example.com/card.png", "카페 전용 카드")),
                List.of(card(INACTIVE_USER_CARD_ID, "KB 국민 일반", null, null))
        ));

        String response = mockMvc.perform(get("/me/cards"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode root = new ObjectMapper().readTree(response);

        assertTrue(root.path("success").asBoolean());
        assertTrue(root.path("data").path("lastSyncedAt").isNull());
        assertEquals(ACTIVE_USER_CARD_ID,
                root.path("data").path("activeCards").get(0).path("userCardId").asText());
        assertEquals("KB카드",
                root.path("data").path("activeCards").get(0).path("issuerName").asText());
        assertEquals(ISSUER_ID,
                root.path("data").path("activeCards").get(0).path("issuerId").asText());
        assertTrue(root.path("data").path("inactiveCards").get(0).path("cardImageUrl").isNull());
        verify(cardQueryService).getMyCards(USER_ID, false);
    }

    @Test
    @DisplayName("includeInactive이 true이면 비활성 카드 필드를 생략한다")
    void omitsInactiveCardsWhenIncludeInactiveIsTrue() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(cardQueryService.getMyCards(USER_ID, true))
                .thenReturn(new MeCardsResponse(null, List.of(), null));

        String response = mockMvc.perform(get("/me/cards").param("includeInactive", "true"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode data = new ObjectMapper().readTree(response).path("data");

        assertTrue(data.path("activeCards").isArray());
        assertTrue(data.path("activeCards").isEmpty());
        assertFalse(data.has("inactiveCards"));
        verify(cardQueryService).getMyCards(USER_ID, true);
    }

    @Test
    @DisplayName("인증 정보가 없으면 카드 서비스를 호출하지 않고 401을 반환한다")
    void rejectsUnauthenticatedRequest() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenThrow(new AuthenticationRequiredException());

        String response = mockMvc.perform(get("/me/cards"))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(response.contains("\"success\":false"));
        verifyNoInteractions(cardQueryService);
    }

    private MeCardItemResponse card(String userCardId, String cardName, String cardImageUrl, String memo) {
        UserCardListRow cardRow = new UserCardListRow();
        cardRow.setUserCardId(userCardId);
        cardRow.setCardName(cardName);
        cardRow.setIssuerId(ISSUER_ID);
        cardRow.setIssuerName("KB카드");
        cardRow.setCardImageUrl(cardImageUrl);
        cardRow.setMemo(memo);
        return new MeCardItemResponse(cardRow);
    }
}
