package com.moca.mocabe.domain.codef.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moca.mocabe.domain.codef.dto.ActivateCardLinkCardsRequest;
import com.moca.mocabe.domain.codef.dto.ActivateCardLinkCardsResponse;
import com.moca.mocabe.domain.codef.dto.CardLinkCardResponse;
import com.moca.mocabe.domain.codef.dto.CardLinkResponse;
import com.moca.mocabe.domain.codef.dto.CreateCardLinkRequest;
import com.moca.mocabe.domain.codef.dto.SubmitCardCredentialsRequest;
import com.moca.mocabe.domain.codef.dto.SyncOwnedCardsResponse;
import com.moca.mocabe.domain.codef.dto.SyncOwnedCardsResult;
import com.moca.mocabe.domain.codef.exception.CardLinkNotFoundException;
import com.moca.mocabe.domain.codef.exception.CodefAccountAlreadyLinkedException;
import com.moca.mocabe.domain.codef.exception.CodefConnectionNotFoundException;
import com.moca.mocabe.domain.codef.exception.IssuerNotFoundException;
import com.moca.mocabe.domain.codef.exception.InvalidCardSelectionException;
import com.moca.mocabe.domain.codef.service.CardLinkService;
import com.moca.mocabe.global.auth.CurrentUserProvider;
import com.moca.mocabe.global.exception.GlobalExceptionHandler;
import com.moca.mocabe.global.exception.auth.AuthenticationRequiredException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CardLinkControllerTest {

    private static final String USER_ID = "01980d6a-5c0c-7aaf-9b85-010203040506";
    private static final String ISSUER_ID = "00000000-0000-4000-8000-000000000301";
    private static final String INSTITUTION_CODE = "0301";
    private static final String REQUEST_BODY =
            "{\"institutionCode\":\"" + INSTITUTION_CODE + "\",\"id\":\"tester\",\"password\":\"secret-pw\"}";

    private CardLinkService cardLinkService;
    private CurrentUserProvider currentUserProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        cardLinkService = org.mockito.Mockito.mock(CardLinkService.class);
        currentUserProvider = org.mockito.Mockito.mock(CurrentUserProvider.class);
        CardLinkController controller = new CardLinkController(cardLinkService, currentUserProvider);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    @Test
    @DisplayName("연동을 생성하면 201과 linkId를 data로 반환한다")
    void createsCardLink() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(cardLinkService.createLink(eq(USER_ID), any(CreateCardLinkRequest.class)))
                .thenReturn(new CardLinkResponse("link-1", INSTITUTION_CODE, "ACTIVE"));

        String response = mockMvc.perform(post("/card-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode root = new ObjectMapper().readTree(response);

        assertTrue(root.path("success").asBoolean());
        assertEquals("link-1", root.path("data").path("linkId").asText());
        assertEquals(INSTITUTION_CODE, root.path("data").path("institutionCode").asText());
        assertEquals("ACTIVE", root.path("data").path("status").asText());
        verify(cardLinkService).createLink(eq(USER_ID), any(CreateCardLinkRequest.class));
    }

    @Test
    @DisplayName("인증 정보가 없으면 서비스를 호출하지 않고 401을 반환한다")
    void rejectsUnauthenticatedRequest() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenThrow(new AuthenticationRequiredException());

        mockMvc.perform(post("/card-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(cardLinkService);
    }

    @Test
    @DisplayName("등록되지 않은 발급사면 식별 가능한 404 오류를 반환한다")
    void rejectsUnknownIssuer() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(cardLinkService.createLink(eq(USER_ID), any(CreateCardLinkRequest.class)))
                .thenThrow(new IssuerNotFoundException(ISSUER_ID));

        String response = mockMvc.perform(post("/card-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode root = new ObjectMapper().readTree(response);

        assertEquals("ISSUER_NOT_FOUND", root.path("error").path("code").asText());
        assertEquals("등록되지 않은 발급사입니다: " + ISSUER_ID,
                root.path("error").path("message").asText());
    }

    @Test
    @DisplayName("이미 연동된 계정이면 409 오류를 반환한다")
    void rejectsDuplicatedLink() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(cardLinkService.createLink(eq(USER_ID), any(CreateCardLinkRequest.class)))
                .thenThrow(new CodefAccountAlreadyLinkedException());

        String response = mockMvc.perform(post("/card-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isConflict())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = new ObjectMapper().readTree(response);
        assertEquals("CODEF_ACCOUNT_ALREADY_LINKED", root.path("error").path("code").asText());
    }

    @Test
    @DisplayName("카드 활성화 요청을 현재 사용자 기준으로 서비스에 전달한다")
    void activatesCards() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(cardLinkService.activateCards(eq(USER_ID), eq("link-1"), any(ActivateCardLinkCardsRequest.class)))
                .thenReturn(new ActivateCardLinkCardsResponse("link-1", List.of("uc-1"), 1));

        String response = mockMvc.perform(patch("/card-links/link-1/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activeUserCardIds\":[\"uc-1\"]}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = new ObjectMapper().readTree(response);
        assertEquals(1, root.path("data").path("activatedCount").asInt());
        verify(cardLinkService).activateCards(
                eq(USER_ID), eq("link-1"), any(ActivateCardLinkCardsRequest.class));
    }

    @Test
    @DisplayName("활성화 대상이 비어 있으면 400 검증 오류를 반환한다")
    void rejectsEmptyActivationBody() throws Exception {
        mockMvc.perform(patch("/card-links/link-1/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("소유하지 않은 연동이면 404를 반환한다")
    void rejectsUnknownCardLink() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(cardLinkService.activateCards(eq(USER_ID), eq("link-1"), any(ActivateCardLinkCardsRequest.class)))
                .thenThrow(new CardLinkNotFoundException());

        String response = mockMvc.perform(patch("/card-links/link-1/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activeUserCardIds\":[\"uc-1\"]}"))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertEquals("CARD_LINK_NOT_FOUND",
                new ObjectMapper().readTree(response).path("error").path("code").asText());
    }

    @Test
    @DisplayName("활성화 대상·옵션 선택이 잘못되면 400을 반환한다")
    void rejectsInvalidCardSelection() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(cardLinkService.activateCards(eq(USER_ID), eq("link-1"), any(ActivateCardLinkCardsRequest.class)))
                .thenThrow(new InvalidCardSelectionException("잘못된 선택"));

        String response = mockMvc.perform(patch("/card-links/link-1/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activeUserCardIds\":[\"uc-1\"]}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertEquals("INVALID_CARD_SELECTION",
                new ObjectMapper().readTree(response).path("error").path("code").asText());
    }

    @Test
    @DisplayName("보유카드 재조회 요청을 institutionCode와 함께 서비스에 전달한다")
    void syncsOwnedCards() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(cardLinkService.syncOwnedCards(USER_ID, INSTITUTION_CODE)).thenReturn(new SyncOwnedCardsResponse(
                List.of(new SyncOwnedCardsResult("link-1", INSTITUTION_CODE, true, List.of()))));

        String response = mockMvc.perform(post("/card-links/cards/sync")
                        .param("institutionCode", INSTITUTION_CODE))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = new ObjectMapper().readTree(response);
        assertTrue(root.path("data").path("results").get(0).path("success").asBoolean());
        verify(cardLinkService).syncOwnedCards(USER_ID, INSTITUTION_CODE);
    }

    @Test
    @DisplayName("인증 정보가 없으면 서비스를 호출하지 않고 401을 반환한다")
    void rejectsUnauthenticatedSyncRequest() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenThrow(new AuthenticationRequiredException());

        mockMvc.perform(post("/card-links/cards/sync")
                        .param("institutionCode", INSTITUTION_CODE))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(cardLinkService);
    }

    @Test
    @DisplayName("institutionCode 없이 재조회하면 null로 서비스에 전달한다")
    void syncsOwnedCardsWithoutInstitutionCode() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(cardLinkService.syncOwnedCards(USER_ID, null)).thenReturn(new SyncOwnedCardsResponse(List.of()));

        mockMvc.perform(post("/card-links/cards/sync"))
                .andExpect(status().isOk());

        verify(cardLinkService).syncOwnedCards(USER_ID, null);
    }

    @Test
    @DisplayName("지정한 기관코드로 연동된 계정이 없으면 404를 반환한다")
    void rejectsSyncWithUnknownInstitutionCode() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(cardLinkService.syncOwnedCards(USER_ID, INSTITUTION_CODE))
                .thenThrow(new CodefConnectionNotFoundException(INSTITUTION_CODE));

        String response = mockMvc.perform(post("/card-links/cards/sync")
                        .param("institutionCode", INSTITUTION_CODE))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertEquals("CODEF_CONNECTION_NOT_FOUND",
                new ObjectMapper().readTree(response).path("error").path("code").asText());
    }

    @Test
    @DisplayName("카드정보 추가 입력 요청을 현재 사용자·userCardId 기준으로 서비스에 전달한다")
    void submitsCardCredentials() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(cardLinkService.submitCardCredentials(
                eq(USER_ID), eq("uc-1"), any(SubmitCardCredentialsRequest.class)))
                .thenReturn(new CardLinkCardResponse("uc-1", "card-1", "정식 카드명", "9999****6666",
                        INSTITUTION_CODE, "KB국민카드", "CREDIT", null, true, true, List.of()));

        String response = mockMvc.perform(patch("/card-links/cards/uc-1/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardNo\":\"9999888877776666\",\"cardPassword\":\"1234\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = new ObjectMapper().readTree(response);
        assertEquals("uc-1", root.path("data").path("userCardId").asText());
        verify(cardLinkService).submitCardCredentials(
                eq(USER_ID), eq("uc-1"), any(SubmitCardCredentialsRequest.class));
    }
}
