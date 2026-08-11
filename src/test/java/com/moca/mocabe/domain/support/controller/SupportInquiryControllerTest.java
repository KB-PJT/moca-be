package com.moca.mocabe.domain.support.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moca.mocabe.domain.support.dto.InquiryResponse;
import com.moca.mocabe.domain.support.model.InquiryRow;
import com.moca.mocabe.domain.support.service.SupportInquiryService;
import com.moca.mocabe.global.auth.CurrentUserProvider;
import com.moca.mocabe.global.exception.GlobalExceptionHandler;
import com.moca.mocabe.global.exception.auth.AuthenticationRequiredException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SupportInquiryControllerTest {

    private static final String USER_ID = "01980d6a-5c0c-7aaf-9b85-010203040506";
    private static final String INQUIRY_ID = "01980d6a-5c0c-7aaf-9b85-010203040531";
    private static final String VALID_BODY = "{"
            + "\"inquiryType\":\"card_link\","
            + "\"title\":\"카드 연동이 안 돼요\","
            + "\"content\":\"계속 실패합니다.\","
            + "\"replyEmail\":\"kakao_jimin@kakao.com\"}";

    private SupportInquiryService supportInquiryService;
    private CurrentUserProvider currentUserProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        supportInquiryService = Mockito.mock(SupportInquiryService.class);
        currentUserProvider = Mockito.mock(CurrentUserProvider.class);
        SupportInquiryController controller =
                new SupportInquiryController(supportInquiryService, currentUserProvider);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    @Test
    @DisplayName("문의 등록 요청을 현재 사용자 기준으로 서비스에 전달하고 201을 반환한다")
    void createsInquiry() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(supportInquiryService.createInquiry(USER_ID, "card_link", "카드 연동이 안 돼요",
                "계속 실패합니다.", "kakao_jimin@kakao.com"))
                .thenReturn(inquiry());

        String response = mockMvc.perform(post("/support/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode root = new ObjectMapper().readTree(response);

        assertTrue(root.path("success").asBoolean());
        assertEquals(INQUIRY_ID, root.path("data").path("inquiryId").asText());
        assertEquals("card_link", root.path("data").path("inquiryType").asText());
        assertEquals("카드 연동이 안 돼요", root.path("data").path("title").asText());
        assertEquals("계속 실패합니다.", root.path("data").path("content").asText());
        assertEquals("kakao_jimin@kakao.com", root.path("data").path("replyEmail").asText());
        assertEquals("received", root.path("data").path("status").asText());
        verify(supportInquiryService).createInquiry(
                USER_ID, "card_link", "카드 연동이 안 돼요", "계속 실패합니다.", "kakao_jimin@kakao.com");
    }

    @Test
    @DisplayName("inquiryType이 허용된 값이 아니면 400을 반환한다")
    void rejectsInvalidInquiryType() throws Exception {
        mockMvc.perform(post("/support/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inquiryType\":\"unknown\",\"title\":\"제목\",\"content\":\"내용\","
                                + "\"replyEmail\":\"kakao_jimin@kakao.com\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(supportInquiryService);
    }

    @Test
    @DisplayName("title이 비어 있으면 400 검증 오류를 반환하고 서비스를 호출하지 않는다")
    void rejectsBlankTitle() throws Exception {
        mockMvc.perform(post("/support/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inquiryType\":\"card_link\",\"title\":\"\",\"content\":\"내용\","
                                + "\"replyEmail\":\"kakao_jimin@kakao.com\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(supportInquiryService);
    }

    @Test
    @DisplayName("content가 비어 있으면 400 검증 오류를 반환하고 서비스를 호출하지 않는다")
    void rejectsBlankContent() throws Exception {
        mockMvc.perform(post("/support/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inquiryType\":\"card_link\",\"title\":\"제목\",\"content\":\"\","
                                + "\"replyEmail\":\"kakao_jimin@kakao.com\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(supportInquiryService);
    }

    @Test
    @DisplayName("title이 100자를 초과하면 400을 반환한다")
    void rejectsTooLongTitle() throws Exception {
        String tooLong = "a".repeat(101);

        mockMvc.perform(post("/support/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inquiryType\":\"card_link\",\"title\":\"" + tooLong + "\",\"content\":\"내용\","
                                + "\"replyEmail\":\"kakao_jimin@kakao.com\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(supportInquiryService);
    }

    @Test
    @DisplayName("content가 2000자를 초과하면 400을 반환한다")
    void rejectsTooLongContent() throws Exception {
        String tooLong = "a".repeat(2001);

        mockMvc.perform(post("/support/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inquiryType\":\"card_link\",\"title\":\"제목\",\"content\":\"" + tooLong + "\","
                                + "\"replyEmail\":\"kakao_jimin@kakao.com\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(supportInquiryService);
    }

    @Test
    @DisplayName("replyEmail이 비어 있으면 400을 반환한다")
    void rejectsBlankReplyEmail() throws Exception {
        mockMvc.perform(post("/support/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inquiryType\":\"card_link\",\"title\":\"제목\",\"content\":\"내용\","
                                + "\"replyEmail\":\"\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(supportInquiryService);
    }

    @Test
    @DisplayName("replyEmail이 이메일 형식이 아니면 400을 반환한다")
    void rejectsInvalidReplyEmailFormat() throws Exception {
        mockMvc.perform(post("/support/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inquiryType\":\"card_link\",\"title\":\"제목\",\"content\":\"내용\","
                                + "\"replyEmail\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(supportInquiryService);
    }

    @Test
    @DisplayName("인증 정보가 없으면 문의 등록 서비스를 호출하지 않고 401을 반환한다")
    void rejectsUnauthenticatedRequest() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenThrow(new AuthenticationRequiredException());

        mockMvc.perform(post("/support/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(supportInquiryService);
    }

    private InquiryResponse inquiry() {
        InquiryRow row = new InquiryRow();
        row.setInquiryId(INQUIRY_ID);
        row.setInquiryType("card_link");
        row.setTitle("카드 연동이 안 돼요");
        row.setContent("계속 실패합니다.");
        row.setReplyEmail("kakao_jimin@kakao.com");
        row.setStatus("received");
        row.setCreatedAt(LocalDateTime.of(2026, 8, 10, 10, 30));
        return new InquiryResponse(row);
    }
}
