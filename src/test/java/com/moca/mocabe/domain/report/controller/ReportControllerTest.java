package com.moca.mocabe.domain.report.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moca.mocabe.domain.report.dto.BenefitCategoriesReportResponse;
import com.moca.mocabe.domain.report.dto.BenefitSummaryReportResponse;
import com.moca.mocabe.domain.report.dto.PerformanceCardsReportResponse;
import com.moca.mocabe.domain.report.dto.PerformanceSummaryReportResponse;
import com.moca.mocabe.domain.report.service.ReportQueryService;
import com.moca.mocabe.global.auth.CurrentUserProvider;
import com.moca.mocabe.global.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ReportControllerTest {

    private static final String USER_ID = "user-1";
    private ReportQueryService reportQueryService;
    private CurrentUserProvider currentUserProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reportQueryService = org.mockito.Mockito.mock(ReportQueryService.class);
        currentUserProvider = org.mockito.Mockito.mock(CurrentUserProvider.class);
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        mockMvc = MockMvcBuilders.standaloneSetup(new ReportController(reportQueryService, currentUserProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void delegatesAllReportQueriesUsingAuthenticatedUser() throws Exception {
        when(reportQueryService.getBenefitSummary(USER_ID, "2026-07"))
                .thenReturn(new BenefitSummaryReportResponse("2026-07", 1, 0, 1, List.of()));
        when(reportQueryService.getBenefitCategories(USER_ID, "2026-07", 2))
                .thenReturn(new BenefitCategoriesReportResponse("2026-07", List.of()));
        when(reportQueryService.getPerformanceSummary(USER_ID, "2026-07"))
                .thenReturn(new PerformanceSummaryReportResponse("2026-07", 0, 0, List.of()));
        when(reportQueryService.getPerformanceCards(USER_ID, "2026-07"))
                .thenReturn(new PerformanceCardsReportResponse("2026-07", List.of()));

        mockMvc.perform(get("/reports/benefits/summary").param("yearMonth", "2026-07"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/reports/benefits/categories").param("yearMonth", "2026-07").param("limit", "2"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/reports/performances/summary").param("yearMonth", "2026-07"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/reports/performances/cards").param("yearMonth", "2026-07"))
                .andExpect(status().isOk());

        verify(reportQueryService).getBenefitSummary(USER_ID, "2026-07");
        verify(reportQueryService).getBenefitCategories(USER_ID, "2026-07", 2);
        verify(reportQueryService).getPerformanceSummary(USER_ID, "2026-07");
        verify(reportQueryService).getPerformanceCards(USER_ID, "2026-07");
    }
}
