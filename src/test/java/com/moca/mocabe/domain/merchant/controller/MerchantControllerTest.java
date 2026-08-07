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
import com.moca.mocabe.domain.merchant.dto.NearbyMerchantResponse;
import com.moca.mocabe.domain.merchant.service.MerchantCategoryQueryService;
import com.moca.mocabe.domain.merchant.service.MerchantNearbyQueryService;
import com.moca.mocabe.domain.merchant.service.MerchantQueryService;
import com.moca.mocabe.global.exception.GlobalExceptionHandler;
import com.moca.mocabe.global.exception.merchant.InvalidMerchantQueryException;
import com.moca.mocabe.global.exception.merchant.KakaoUnavailableException;
import com.moca.mocabe.global.exception.merchant.MerchantCategoryNotFoundException;
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
    private MerchantNearbyQueryService merchantNearbyQueryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        merchantCategoryQueryService = mock(MerchantCategoryQueryService.class);
        merchantQueryService = mock(MerchantQueryService.class);
        merchantNearbyQueryService = mock(MerchantNearbyQueryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new MerchantController(merchantCategoryQueryService, merchantQueryService,
                                merchantNearbyQueryService))
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

    @Test
    @DisplayName("존재하지 않는 categoryId면 가맹점 목록 API가 404를 반환한다")
    void returnsNotFoundForUnknownCategoryOnMerchants() throws Exception {
        when(merchantQueryService.getMerchantsByCategory("cat-unknown"))
                .thenThrow(new MerchantCategoryNotFoundException("존재하지 않는 카테고리입니다. categoryId=cat-unknown"));

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode response = objectMapper.readTree(mockMvc.perform(get("/merchants")
                        .param("categoryId", "cat-unknown"))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString());

        assertEquals("MERCHANT_CATEGORY_NOT_FOUND", response.path("error").path("code").asText());
    }

    @Test
    @DisplayName("근처 가맹점 API는 쿼리값을 서비스에 전달하고 결과를 반환한다")
    void returnsNearbyMerchants() throws Exception {
        when(merchantNearbyQueryService.getNearbyMerchants("cat-cafe", 37.5, 127.0, 500)).thenReturn(List.of(
                new NearbyMerchantResponse("m-1", "스타벅스", 37.501, 127.001, 120)));

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode response = objectMapper.readTree(mockMvc.perform(get("/merchants/nearby")
                        .param("categoryId", "cat-cafe")
                        .param("latitude", "37.5")
                        .param("longitude", "127.0")
                        .param("radiusMeters", "500"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertTrue(response.path("success").asBoolean());
        assertEquals("스타벅스", response.path("data").get(0).path("name").asText());
        assertEquals(120, response.path("data").get(0).path("distanceMeters").asInt());
    }

    @Test
    @DisplayName("근처 가맹점 API는 radiusMeters 생략을 허용한다")
    void allowsOmittingRadiusForNearbyMerchants() throws Exception {
        when(merchantNearbyQueryService.getNearbyMerchants("cat-cafe", 37.5, 127.0, null)).thenReturn(List.of());

        mockMvc.perform(get("/merchants/nearby")
                        .param("categoryId", "cat-cafe")
                        .param("latitude", "37.5")
                        .param("longitude", "127.0"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("근처 가맹점 API는 latitude가 없으면 400을 반환한다(Spring MVC 필수 파라미터 검증)")
    void rejectsMissingLatitudeForNearbyMerchants() throws Exception {
        mockMvc.perform(get("/merchants/nearby")
                        .param("categoryId", "cat-cafe")
                        .param("longitude", "127.0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("존재하지 않는 categoryId면 근처 가맹점 API가 404를 반환한다")
    void returnsNotFoundForUnknownCategoryOnNearbyMerchants() throws Exception {
        when(merchantNearbyQueryService.getNearbyMerchants("cat-unknown", 37.5, 127.0, 500))
                .thenThrow(new MerchantCategoryNotFoundException("존재하지 않는 카테고리입니다. categoryId=cat-unknown"));

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode response = objectMapper.readTree(mockMvc.perform(get("/merchants/nearby")
                        .param("categoryId", "cat-unknown")
                        .param("latitude", "37.5")
                        .param("longitude", "127.0")
                        .param("radiusMeters", "500"))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString());

        assertEquals("MERCHANT_CATEGORY_NOT_FOUND", response.path("error").path("code").asText());
    }

    @Test
    @DisplayName("카카오맵 상류 장애면 503을 반환한다")
    void returnsServiceUnavailableWhenKakaoFails() throws Exception {
        when(merchantNearbyQueryService.getNearbyMerchants("cat-cafe", 37.5, 127.0, 500))
                .thenThrow(new KakaoUnavailableException("카카오맵 응답 오류(HTTP 500)"));

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode response = objectMapper.readTree(mockMvc.perform(get("/merchants/nearby")
                        .param("categoryId", "cat-cafe")
                        .param("latitude", "37.5")
                        .param("longitude", "127.0")
                        .param("radiusMeters", "500"))
                .andExpect(status().isServiceUnavailable()).andReturn().getResponse().getContentAsString());

        assertEquals("KAKAO_UNAVAILABLE", response.path("error").path("code").asText());
    }
}
