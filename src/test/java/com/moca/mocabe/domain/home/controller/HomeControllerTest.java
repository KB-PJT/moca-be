package com.moca.mocabe.domain.home.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moca.mocabe.domain.home.dto.HomeCardsResponse;
import com.moca.mocabe.domain.home.dto.HomeGreetingResponse;
import com.moca.mocabe.domain.home.dto.RecentBenefitsResponse;
import com.moca.mocabe.domain.home.service.HomeQueryService;
import com.moca.mocabe.global.auth.CurrentUserProvider;
import com.moca.mocabe.global.exception.GlobalExceptionHandler;
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
                .build();
    }

    @Test
    @DisplayName("홈 세 컴포넌트 API는 인증 사용자와 쿼리값을 서비스에 전달한다")
    void delegatesHomeQueries() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(homeQueryService.getGreeting(USER_ID, "2026-07"))
                .thenReturn(new HomeGreetingResponse("지민", "2026-07", 0, "이번 달 놓친 혜택이 없습니다."));
        when(homeQueryService.getCards(USER_ID, "2026-07", "MANUAL"))
                .thenReturn(new HomeCardsResponse("2026-07", "MANUAL", null, List.of()));
        when(homeQueryService.getRecentBenefits(USER_ID, "2026-07", 3))
                .thenReturn(new RecentBenefitsResponse(List.of()));

        mockMvc.perform(get("/home/greeting").param("yearMonth", "2026-07"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/home/cards")
                        .param("yearMonth", "2026-07")
                        .param("orderMode", "MANUAL"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/home/recent-benefits")
                        .param("yearMonth", "2026-07")
                        .param("limit", "3"))
                .andExpect(status().isOk());

        verify(homeQueryService).getGreeting(USER_ID, "2026-07");
        verify(homeQueryService).getCards(USER_ID, "2026-07", "MANUAL");
        verify(homeQueryService).getRecentBenefits(USER_ID, "2026-07", 3);
    }
}
