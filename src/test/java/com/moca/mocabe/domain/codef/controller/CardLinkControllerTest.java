package com.moca.mocabe.domain.codef.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moca.mocabe.domain.codef.dto.CardLinkResponse;
import com.moca.mocabe.domain.codef.dto.CreateCardLinkRequest;
import com.moca.mocabe.domain.codef.exception.CodefAccountAlreadyLinkedException;
import com.moca.mocabe.domain.codef.exception.IssuerNotFoundException;
import com.moca.mocabe.domain.codef.service.CardLinkService;
import com.moca.mocabe.global.auth.CurrentUserProvider;
import com.moca.mocabe.global.exception.GlobalExceptionHandler;
import com.moca.mocabe.global.exception.auth.AuthenticationRequiredException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CardLinkControllerTest {

    private static final String USER_ID = "01980d6a-5c0c-7aaf-9b85-010203040506";
    private static final String ISSUER_ID = "00000000-0000-4000-8000-000000000301";
    private static final String REQUEST_BODY =
            "{\"issuerId\":\"" + ISSUER_ID + "\",\"id\":\"tester\",\"password\":\"secret-pw\"}";

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
                .thenReturn(new CardLinkResponse("link-1", ISSUER_ID, "ACTIVE"));

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
        assertEquals(ISSUER_ID, root.path("data").path("issuerId").asText());
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
}
