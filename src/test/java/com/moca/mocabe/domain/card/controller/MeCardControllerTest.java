package com.moca.mocabe.domain.card.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moca.mocabe.domain.card.dto.CardBenefitResponse;
import com.moca.mocabe.domain.card.dto.CardDetailResponse;
import com.moca.mocabe.domain.card.dto.MeCardItemResponse;
import com.moca.mocabe.domain.card.dto.MeCardsResponse;
import com.moca.mocabe.domain.card.dto.SyncMyCardsResponse;
import com.moca.mocabe.domain.card.exception.InvalidCardOrderException;
import com.moca.mocabe.domain.card.model.CardBenefitRow;
import com.moca.mocabe.domain.card.model.UserCardListRow;
import com.moca.mocabe.domain.card.service.CardQueryService;
import com.moca.mocabe.domain.codef.exception.InvalidSyncPeriodException;
import com.moca.mocabe.domain.codef.exception.UserCardNotFoundException;
import com.moca.mocabe.domain.codef.service.CardSyncService;
import com.moca.mocabe.global.auth.CurrentUserProvider;
import com.moca.mocabe.global.exception.GlobalExceptionHandler;
import com.moca.mocabe.global.exception.auth.AuthenticationRequiredException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MeCardControllerTest {

    private static final String USER_ID = "01980d6a-5c0c-7aaf-9b85-010203040506";
    private static final String ACTIVE_USER_CARD_ID = "01980d6a-5c0c-7aaf-9b85-010203040531";
    private static final String OTHER_ACTIVE_USER_CARD_ID = "01980d6a-5c0c-7aaf-9b85-010203040532";
    private static final String INACTIVE_USER_CARD_ID = "01980d6a-5c0c-7aaf-9b85-010203040533";
    private static final String ISSUER_ID = "00000000-0000-4000-8000-000000000301";

    private CardQueryService cardQueryService;
    private CardSyncService cardSyncService;
    private CurrentUserProvider currentUserProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        cardQueryService = org.mockito.Mockito.mock(CardQueryService.class);
        cardSyncService = org.mockito.Mockito.mock(CardSyncService.class);
        currentUserProvider = org.mockito.Mockito.mock(CurrentUserProvider.class);
        MeCardController controller =
                new MeCardController(cardQueryService, cardSyncService, currentUserProvider);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    @Test
    @DisplayName("쿼리를 생략하면 활성 카드만 data로 반환한다")
    void omitsInactiveCardsByDefault() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(cardQueryService.getMyCards(USER_ID, true)).thenReturn(new MeCardsResponse(
                null,
                List.of(card(ACTIVE_USER_CARD_ID, "KB My WE:SH",
                        "https://example.com/card.png", "카페 전용 카드")),
                null
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
        assertFalse(root.path("data").has("inactiveCards"));
        verify(cardQueryService).getMyCards(USER_ID, true);
    }

    @Test
    @DisplayName("includeInactive이 true이면 활성 카드와 비활성 카드를 data로 반환한다")
    void returnsActiveAndInactiveCardsWhenIncludeInactiveIsTrue() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(cardQueryService.getMyCards(USER_ID, false)).thenReturn(new MeCardsResponse(
                null,
                List.of(),
                List.of(card(INACTIVE_USER_CARD_ID, "KB 국민 일반", null, null))
        ));

        String response = mockMvc.perform(get("/me/cards").param("includeInactive", "true"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode data = new ObjectMapper().readTree(response).path("data");

        assertTrue(data.path("activeCards").isArray());
        assertTrue(data.path("activeCards").isEmpty());
        assertTrue(data.path("inactiveCards").get(0).path("cardImageUrl").isNull());
        verify(cardQueryService).getMyCards(USER_ID, false);
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

    @Test
    @DisplayName("날짜를 생략하면 null 기간으로 동기화를 호출하고 결과를 반환한다")
    void syncsWithDefaultPeriodWhenDatesOmitted() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(cardSyncService.sync(eq(USER_ID), isNull(), isNull(), isNull()))
                .thenReturn(new SyncMyCardsResponse(3, 42, 2, "2026-08-03T10:30:00+09:00"));

        String response = mockMvc.perform(post("/me/cards/sync"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode data = new ObjectMapper().readTree(response).path("data");

        assertEquals(3, data.path("syncedCardCount").asInt());
        assertEquals(42, data.path("syncedApprovalCount").asInt());
        assertEquals(2, data.path("syncedPerformanceCount").asInt());
        assertEquals("2026-08-03T10:30:00+09:00", data.path("syncedAt").asText());
        verify(cardSyncService).sync(USER_ID, null, null, null);
    }

    @Test
    @DisplayName("startDate/endDate를 전달하면 파싱해 동기화에 넘긴다")
    void syncsWithProvidedPeriod() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(cardSyncService.sync(
                        eq(USER_ID), eq(LocalDate.of(2026, 7, 1)), eq(LocalDate.of(2026, 7, 31)), isNull()))
                .thenReturn(new SyncMyCardsResponse(1, 5, 1, "2026-08-03T10:30:00+09:00"));

        mockMvc.perform(post("/me/cards/sync")
                        .param("startDate", "2026-07-01")
                        .param("endDate", "2026-07-31"))
                .andExpect(status().isOk());

        verify(cardSyncService)
                .sync(USER_ID, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), null);
    }

    @Test
    @DisplayName("institutionCode를 전달하면 그대로 동기화에 넘긴다")
    void syncsWithProvidedInstitutionCode() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(cardSyncService.sync(eq(USER_ID), isNull(), isNull(), eq("0301")))
                .thenReturn(new SyncMyCardsResponse(1, 5, 1, "2026-08-03T10:30:00+09:00"));

        mockMvc.perform(post("/me/cards/sync").param("institutionCode", "0301"))
                .andExpect(status().isOk());

        verify(cardSyncService).sync(USER_ID, null, null, "0301");
    }

    @Test
    @DisplayName("조회 기간이 올바르지 않으면 400을 반환한다")
    void returnsBadRequestWhenPeriodInvalid() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(cardSyncService.sync(eq(USER_ID), isNull(), isNull(), isNull()))
                .thenThrow(new InvalidSyncPeriodException("조회 시작일이 종료일보다 늦을 수 없습니다."));

        String response = mockMvc.perform(post("/me/cards/sync"))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(response.contains("INVALID_SYNC_PERIOD"));
    }

    @Test
    @DisplayName("인증 정보가 없으면 동기화 서비스를 호출하지 않고 401을 반환한다")
    void rejectsUnauthenticatedSyncRequest() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenThrow(new AuthenticationRequiredException());

        mockMvc.perform(post("/me/cards/sync"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(cardSyncService);
    }

    @Test
    @DisplayName("메모 수정 요청을 현재 사용자·userCardId 기준으로 서비스에 전달한다")
    void updatesMemo() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(cardQueryService.updateMemo(USER_ID, ACTIVE_USER_CARD_ID, "새 메모"))
                .thenReturn(card(ACTIVE_USER_CARD_ID, "KB My WE:SH", null, "새 메모"));

        String response = mockMvc.perform(patch("/me/cards/" + ACTIVE_USER_CARD_ID + "/memo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memo\":\"새 메모\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = new ObjectMapper().readTree(response);
        assertEquals(ACTIVE_USER_CARD_ID, root.path("data").path("userCardId").asText());
        assertEquals("새 메모", root.path("data").path("memo").asText());
        verify(cardQueryService).updateMemo(USER_ID, ACTIVE_USER_CARD_ID, "새 메모");
    }

    @Test
    @DisplayName("메모를 생략하면 null로 서비스에 전달한다")
    void updatesMemoWithNullWhenOmitted() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(cardQueryService.updateMemo(eq(USER_ID), eq(ACTIVE_USER_CARD_ID), isNull()))
                .thenReturn(card(ACTIVE_USER_CARD_ID, "KB My WE:SH", null, null));

        mockMvc.perform(patch("/me/cards/" + ACTIVE_USER_CARD_ID + "/memo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        verify(cardQueryService).updateMemo(eq(USER_ID), eq(ACTIVE_USER_CARD_ID), isNull());
    }

    @Test
    @DisplayName("메모가 500자를 초과하면 400 검증 오류를 반환하고 서비스를 호출하지 않는다")
    void rejectsTooLongMemo() throws Exception {
        String tooLong = "a".repeat(501);

        mockMvc.perform(patch("/me/cards/" + ACTIVE_USER_CARD_ID + "/memo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memo\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(cardQueryService);
    }

    @Test
    @DisplayName("non-BMP 문자 500 코드포인트는 UTF-16 단위로 1000이어도 통과한다")
    void allowsNonBmpMemoAtCodePointLimit() throws Exception {
        String emoji = new String(Character.toChars(0x1F600));
        String memo = emoji.repeat(500);
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(cardQueryService.updateMemo(USER_ID, ACTIVE_USER_CARD_ID, memo))
                .thenReturn(card(ACTIVE_USER_CARD_ID, "KB My WE:SH", null, memo));

        mockMvc.perform(patch("/me/cards/" + ACTIVE_USER_CARD_ID + "/memo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(java.util.Map.of("memo", memo))))
                .andExpect(status().isOk());

        verify(cardQueryService).updateMemo(USER_ID, ACTIVE_USER_CARD_ID, memo);
    }

    @Test
    @DisplayName("non-BMP 문자가 500 코드포인트를 초과하면 400을 반환한다")
    void rejectsNonBmpMemoOverCodePointLimit() throws Exception {
        String emoji = new String(Character.toChars(0x1F600));
        String memo = emoji.repeat(501);

        mockMvc.perform(patch("/me/cards/" + ACTIVE_USER_CARD_ID + "/memo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(java.util.Map.of("memo", memo))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(cardQueryService);
    }

    @Test
    @DisplayName("인증 정보가 없으면 메모 수정 서비스를 호출하지 않고 401을 반환한다")
    void rejectsUnauthenticatedMemoUpdate() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenThrow(new AuthenticationRequiredException());

        mockMvc.perform(patch("/me/cards/" + ACTIVE_USER_CARD_ID + "/memo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memo\":\"새 메모\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(cardQueryService);
    }

    @Test
    @DisplayName("본인 소유 카드가 아니면 404를 반환한다")
    void rejectsUnknownCardForMemoUpdate() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(cardQueryService.updateMemo(USER_ID, ACTIVE_USER_CARD_ID, "새 메모"))
                .thenThrow(new UserCardNotFoundException());

        String response = mockMvc.perform(patch("/me/cards/" + ACTIVE_USER_CARD_ID + "/memo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memo\":\"새 메모\"}"))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertEquals("USER_CARD_NOT_FOUND",
                new ObjectMapper().readTree(response).path("error").path("code").asText());
    }

    @Test
    @DisplayName("카드 상세정보 조회 요청을 현재 사용자·userCardId 기준으로 서비스에 전달한다")
    void getsCardDetail() throws Exception {
        UserCardListRow cardRow = new UserCardListRow();
        cardRow.setUserCardId(ACTIVE_USER_CARD_ID);
        cardRow.setCardName("KB My WE:SH");
        cardRow.setIssuerId(ISSUER_ID);
        cardRow.setIssuerName("KB카드");
        cardRow.setCardImageUrl("https://example.com/card.png");
        cardRow.setMemo("배달 귀요미 카드");
        CardBenefitRow benefitRow = new CardBenefitRow();
        benefitRow.setBenefitId("01980d6a-5c0c-7aaf-9b85-010203040550");
        benefitRow.setRecordType("benefit");
        benefitRow.setTitle("카페 10% 할인");
        benefitRow.setSummary("월 최대 5,000원");
        benefitRow.setDetailText("카페 상세");
        benefitRow.setDetailHtml("<p>카페 상세</p>");

        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(cardQueryService.getCardDetail(USER_ID, ACTIVE_USER_CARD_ID)).thenReturn(
                new CardDetailResponse(cardRow, List.of(new CardBenefitResponse(benefitRow)), List.of()));

        String response = mockMvc.perform(get("/me/cards/" + ACTIVE_USER_CARD_ID))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = new ObjectMapper().readTree(response).path("data");

        assertEquals(ACTIVE_USER_CARD_ID, data.path("userCardId").asText());
        assertEquals("배달 귀요미 카드", data.path("memo").asText());
        assertEquals("카페 10% 할인", data.path("benefits").get(0).path("title").asText());
        assertEquals("카페 상세", data.path("benefits").get(0).path("detailText").asText());
        assertTrue(data.path("notices").isEmpty());
        verify(cardQueryService).getCardDetail(USER_ID, ACTIVE_USER_CARD_ID);
    }

    @Test
    @DisplayName("본인 소유 카드가 아니면 상세정보 조회도 404를 반환한다")
    void rejectsUnknownCardForDetail() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(cardQueryService.getCardDetail(USER_ID, ACTIVE_USER_CARD_ID))
                .thenThrow(new UserCardNotFoundException());

        String response = mockMvc.perform(get("/me/cards/" + ACTIVE_USER_CARD_ID))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertEquals("USER_CARD_NOT_FOUND",
                new ObjectMapper().readTree(response).path("error").path("code").asText());
    }

    @Test
    @DisplayName("인증 정보가 없으면 상세정보 조회 서비스를 호출하지 않고 401을 반환한다")
    void rejectsUnauthenticatedDetailRequest() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenThrow(new AuthenticationRequiredException());

        mockMvc.perform(get("/me/cards/" + ACTIVE_USER_CARD_ID))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(cardQueryService);
    }

    @Test
    @DisplayName("순서 변경 요청을 현재 사용자 기준으로 서비스에 전달하고 갱신된 목록을 반환한다")
    void reordersCards() throws Exception {
        List<String> newOrder = List.of(OTHER_ACTIVE_USER_CARD_ID, ACTIVE_USER_CARD_ID);
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(cardQueryService.reorderCards(USER_ID, newOrder)).thenReturn(new MeCardsResponse(
                null,
                List.of(card(OTHER_ACTIVE_USER_CARD_ID, "KB 국민 일반", null, null),
                        card(ACTIVE_USER_CARD_ID, "KB My WE:SH", null, null)),
                List.of()));

        String response = mockMvc.perform(patch("/me/cards/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userCardIds\":[\"" + OTHER_ACTIVE_USER_CARD_ID
                                + "\",\"" + ACTIVE_USER_CARD_ID + "\"]}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = new ObjectMapper().readTree(response).path("data");
        assertEquals(OTHER_ACTIVE_USER_CARD_ID, data.path("activeCards").get(0).path("userCardId").asText());
        assertEquals(ACTIVE_USER_CARD_ID, data.path("activeCards").get(1).path("userCardId").asText());
        verify(cardQueryService).reorderCards(USER_ID, newOrder);
    }

    @Test
    @DisplayName("userCardIds가 비어 있으면 400 검증 오류를 반환하고 서비스를 호출하지 않는다")
    void rejectsEmptyReorderRequest() throws Exception {
        mockMvc.perform(patch("/me/cards/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userCardIds\":[]}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(cardQueryService);
    }

    @Test
    @DisplayName("요청 카드 목록이 활성 카드 전체와 다르면 400을 반환한다")
    void rejectsReorderWhenCardSetMismatches() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(cardQueryService.reorderCards(USER_ID, List.of(ACTIVE_USER_CARD_ID)))
                .thenThrow(new InvalidCardOrderException("보유한 활성 카드 전체를 중복 없이 포함해야 합니다."));

        String response = mockMvc.perform(patch("/me/cards/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userCardIds\":[\"" + ACTIVE_USER_CARD_ID + "\"]}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertEquals("INVALID_CARD_ORDER",
                new ObjectMapper().readTree(response).path("error").path("code").asText());
    }

    @Test
    @DisplayName("인증 정보가 없으면 순서 변경 서비스를 호출하지 않고 401을 반환한다")
    void rejectsUnauthenticatedReorderRequest() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenThrow(new AuthenticationRequiredException());

        mockMvc.perform(patch("/me/cards/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userCardIds\":[\"" + ACTIVE_USER_CARD_ID + "\"]}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(cardQueryService);
    }

    @Test
    @DisplayName("비활성화 요청을 현재 사용자·userCardId 기준으로 서비스에 전달한다")
    void deactivatesCard() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);

        String response = mockMvc.perform(patch("/me/cards/" + ACTIVE_USER_CARD_ID + "/deactivate"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = new ObjectMapper().readTree(response);
        assertTrue(root.path("success").asBoolean());
        assertTrue(root.path("data").path("success").asBoolean());
        verify(cardQueryService).deactivateCard(USER_ID, ACTIVE_USER_CARD_ID);
    }

    @Test
    @DisplayName("본인 소유 카드가 아니면 비활성화도 404를 반환한다")
    void rejectsUnknownCardForDeactivate() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        org.mockito.Mockito.doThrow(new UserCardNotFoundException())
                .when(cardQueryService).deactivateCard(USER_ID, ACTIVE_USER_CARD_ID);

        String response = mockMvc.perform(patch("/me/cards/" + ACTIVE_USER_CARD_ID + "/deactivate"))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertEquals("USER_CARD_NOT_FOUND",
                new ObjectMapper().readTree(response).path("error").path("code").asText());
    }

    @Test
    @DisplayName("인증 정보가 없으면 비활성화 서비스를 호출하지 않고 401을 반환한다")
    void rejectsUnauthenticatedDeactivateRequest() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenThrow(new AuthenticationRequiredException());

        mockMvc.perform(patch("/me/cards/" + ACTIVE_USER_CARD_ID + "/deactivate"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(cardQueryService);
    }

    @Test
    @DisplayName("연결 해제 요청을 현재 사용자·userCardId 기준으로 서비스에 전달한다")
    void disconnectsCard() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);

        String response = mockMvc.perform(delete("/me/cards/" + ACTIVE_USER_CARD_ID))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = new ObjectMapper().readTree(response);
        assertTrue(root.path("success").asBoolean());
        assertTrue(root.path("data").path("success").asBoolean());
        verify(cardQueryService).disconnectCard(USER_ID, ACTIVE_USER_CARD_ID);
    }

    @Test
    @DisplayName("본인 소유 카드가 아니면 연결 해제도 404를 반환한다")
    void rejectsUnknownCardForDisconnect() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        org.mockito.Mockito.doThrow(new UserCardNotFoundException())
                .when(cardQueryService).disconnectCard(USER_ID, ACTIVE_USER_CARD_ID);

        String response = mockMvc.perform(delete("/me/cards/" + ACTIVE_USER_CARD_ID))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertEquals("USER_CARD_NOT_FOUND",
                new ObjectMapper().readTree(response).path("error").path("code").asText());
    }

    @Test
    @DisplayName("인증 정보가 없으면 연결 해제 서비스를 호출하지 않고 401을 반환한다")
    void rejectsUnauthenticatedDisconnectRequest() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenThrow(new AuthenticationRequiredException());

        mockMvc.perform(delete("/me/cards/" + ACTIVE_USER_CARD_ID))
                .andExpect(status().isUnauthorized());

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
