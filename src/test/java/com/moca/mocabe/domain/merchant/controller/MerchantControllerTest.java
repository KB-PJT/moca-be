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
import com.moca.mocabe.domain.merchant.service.MerchantCategoryQueryService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MerchantControllerTest {

    private MerchantCategoryQueryService merchantCategoryQueryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        merchantCategoryQueryService = mock(MerchantCategoryQueryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new MerchantController(merchantCategoryQueryService))
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
}
