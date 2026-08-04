package com.moca.mocabe.domain.home.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moca.mocabe.domain.home.dto.HomeBenefitHighlightResponse;
import com.moca.mocabe.domain.home.dto.HomeCardsResponse;
import com.moca.mocabe.domain.home.dto.HomeCardResponse;
import com.moca.mocabe.domain.home.dto.HomeCardSummaryResponse;
import com.moca.mocabe.domain.home.dto.HomeGreetingResponse;
import com.moca.mocabe.domain.home.dto.RecentBenefitItemResponse;
import com.moca.mocabe.domain.home.dto.RecentBenefitsResponse;
import com.moca.mocabe.domain.home.service.HomeQueryService;
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

class HomeControllerTest {

    private static final String USER_ID = "01980d6a-5c0c-7aaf-9b85-010203040506";

    private HomeQueryService homeQueryService;
    private CurrentUserProvider currentUserProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        homeQueryService = org.mockito.Mockito.mock(HomeQueryService.class);
        currentUserProvider = org.mockito.Mockito.mock(CurrentUserProvider.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new HomeController(homeQueryService, currentUserProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    @Test
    @DisplayName("홈 세 컴포넌트 API는 인증 사용자와 쿼리값을 서비스에 전달한다")
    void delegatesHomeQueries() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(homeQueryService.getGreeting(USER_ID, "2026-07"))
                .thenReturn(new HomeGreetingResponse("지민", "2026-07", 8200, "이번 달 혜택 8,200원을 놓치고 있어요!"));
        when(homeQueryService.getCards(USER_ID, "2026-07", "MANUAL"))
                .thenReturn(new HomeCardsResponse("2026-07", "MANUAL", "uc-1", List.of(
                        new HomeCardResponse("uc-1", 1, "신한 Mr.Life", "내 카드", "https://image/card.png",
                                null, new HomeBenefitHighlightResponse("카페 10% 할인", "월 최대 5천원"),
                                new HomeCardSummaryResponse(21800, 8200, 30000, 382000, 500000, 76, 118000)))));
        when(homeQueryService.getRecentBenefits(USER_ID, "2026-07", 3))
                .thenReturn(new RecentBenefitsResponse(List.of(new RecentBenefitItemResponse(
                        "benefit-1", "스타벅스", "DISCOUNT", "카페 10% 할인", "신한 Mr.Life",
                        15000, 1500, "2026-07-27T14:30:00+09:00"))));

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode greeting = objectMapper.readTree(mockMvc.perform(get("/home/greeting")
                        .param("yearMonth", "2026-07"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertTrue(greeting.path("success").asBoolean());
        assertEquals("지민", greeting.path("data").path("nickname").asText());
        assertEquals("2026-07", greeting.path("data").path("yearMonth").asText());
        assertEquals(8200, greeting.path("data").path("missedBenefitAmount").asInt());

        JsonNode cards = objectMapper.readTree(mockMvc.perform(get("/home/cards")
                        .param("yearMonth", "2026-07")
                        .param("orderMode", "MANUAL"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertTrue(cards.path("success").asBoolean());
        assertEquals("uc-1", cards.path("data").path("selectedUserCardId").asText());
        assertEquals("신한 Mr.Life", cards.path("data").path("cards").get(0).path("cardName").asText());
        assertEquals(76, cards.path("data").path("cards").get(0).path("summary")
                .path("performanceRate").asInt());

        JsonNode benefits = objectMapper.readTree(mockMvc.perform(get("/home/recent-benefits")
                        .param("yearMonth", "2026-07")
                        .param("limit", "3"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertTrue(benefits.path("success").asBoolean());
        assertEquals("스타벅스", benefits.path("data").path("benefits").get(0)
                .path("merchantName").asText());
        assertEquals(1500, benefits.path("data").path("benefits").get(0)
                .path("benefitAmount").asInt());

        verify(homeQueryService).getGreeting(USER_ID, "2026-07");
        verify(homeQueryService).getCards(USER_ID, "2026-07", "MANUAL");
        verify(homeQueryService).getRecentBenefits(USER_ID, "2026-07", 3);
    }

    @Test
    @DisplayName("인증 정보가 없으면 홈 세 API 모두 401을 반환하고 서비스를 호출하지 않는다")
    void rejectsUnauthenticatedHomeQueries() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenThrow(new AuthenticationRequiredException());

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode greeting = objectMapper.readTree(mockMvc.perform(get("/home/greeting"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString());
        assertTrue(!greeting.path("success").asBoolean());
        assertEquals("AUTHENTICATION_REQUIRED", greeting.path("error").path("code").asText());
        JsonNode cards = objectMapper.readTree(mockMvc.perform(get("/home/cards"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString());
        assertEquals("AUTHENTICATION_REQUIRED", cards.path("error").path("code").asText());
        JsonNode benefits = objectMapper.readTree(mockMvc.perform(get("/home/recent-benefits"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString());
        assertEquals("AUTHENTICATION_REQUIRED", benefits.path("error").path("code").asText());

        verifyNoInteractions(homeQueryService);
    }
}
