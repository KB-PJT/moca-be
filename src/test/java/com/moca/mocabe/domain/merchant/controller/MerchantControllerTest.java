package com.moca.mocabe.domain.merchant.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moca.mocabe.domain.merchant.dto.MerchantCategoryResponse;
import com.moca.mocabe.domain.merchant.dto.MerchantResponse;
import com.moca.mocabe.domain.merchant.service.MerchantCategoryQueryService;
import com.moca.mocabe.domain.merchant.service.MerchantQueryService;
import com.moca.mocabe.global.exception.GlobalExceptionHandler;
import com.moca.mocabe.global.exception.merchant.InvalidMerchantQueryException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MerchantControllerTest {

    private MerchantCategoryQueryService merchantCategoryQueryService;
    private MerchantQueryService merchantQueryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        merchantCategoryQueryService = mock(MerchantCategoryQueryService.class);
        merchantQueryService = mock(MerchantQueryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new MerchantController(merchantCategoryQueryService, merchantQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    @Test
    @DisplayName("카테고리 목록 API는 서비스 결과를 그대로 반환한다")
    void returnsCategories() throws Exception {
        when(merchantCategoryQueryService.getCategories()).thenReturn(List.of(
                new MerchantCategoryResponse("cat-cafe", "CAFE", "카페")));

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode response = objectMapper.readTree(mockMvc.perform(get("/merchants/categories"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertTrue(response.path("success").asBoolean());
        assertEquals("CAFE", response.path("data").get(0).path("categoryCode").asText());
    }

    @Test
    @DisplayName("가맹점 목록 API는 categoryId를 서비스에 전달하고 결과를 반환한다")
    void returnsMerchantsForCategory() throws Exception {
        when(merchantQueryService.getMerchantsByCategory("cat-cafe")).thenReturn(List.of(
                new MerchantResponse("m-1", "스타벅스")));

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode response = objectMapper.readTree(mockMvc.perform(get("/merchants").param("categoryId", "cat-cafe"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertTrue(response.path("success").asBoolean());
        assertEquals("스타벅스", response.path("data").get(0).path("name").asText());
    }

    @Test
    @DisplayName("categoryId 쿼리 파라미터가 없으면 400을 반환한다(Spring MVC 필수 파라미터 검증)")
    void rejectsMissingCategoryId() throws Exception {
        mockMvc.perform(get("/merchants")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("categoryId가 빈 값이면 서비스가 던진 예외를 400으로 변환한다")
    void rejectsBlankCategoryId() throws Exception {
        when(merchantQueryService.getMerchantsByCategory(""))
                .thenThrow(new InvalidMerchantQueryException("categoryId는 필수입니다."));

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode response = objectMapper.readTree(mockMvc.perform(get("/merchants").param("categoryId", ""))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString());

        assertEquals("INVALID_MERCHANT_QUERY", response.path("error").path("code").asText());
    }
}
