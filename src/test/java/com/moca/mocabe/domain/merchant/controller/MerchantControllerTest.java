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
import com.moca.mocabe.domain.merchant.dto.MerchantCardRecommendationResponse;
import com.moca.mocabe.domain.merchant.dto.MerchantSummaryResponse;
import com.moca.mocabe.domain.user.type.BenefitPreferenceType;
import com.moca.mocabe.domain.merchant.service.MerchantCategoryQueryService;
import com.moca.mocabe.domain.merchant.service.MerchantNearbyQueryService;
import com.moca.mocabe.domain.merchant.service.MerchantCardRecommendationService;
import com.moca.mocabe.global.auth.CurrentUserProvider;
import com.moca.mocabe.domain.merchant.service.MerchantQueryService;
import com.moca.mocabe.global.exception.GlobalExceptionHandler;
import com.moca.mocabe.global.exception.merchant.InvalidMerchantQueryException;
import com.moca.mocabe.global.exception.merchant.KakaoUnavailableException;
import com.moca.mocabe.global.exception.merchant.MerchantCategoryNotFoundException;
import com.moca.mocabe.global.exception.merchant.MerchantNotFoundException;
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
    private MerchantCardRecommendationService merchantCardRecommendationService;
    private CurrentUserProvider currentUserProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        merchantCategoryQueryService = mock(MerchantCategoryQueryService.class);
        merchantQueryService = mock(MerchantQueryService.class);
        merchantNearbyQueryService = mock(MerchantNearbyQueryService.class);
        merchantCardRecommendationService = mock(MerchantCardRecommendationService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new MerchantController(merchantCategoryQueryService, merchantQueryService,
                                merchantNearbyQueryService, merchantCardRecommendationService,
                                currentUserProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    @Test
    @DisplayName("가맹점 카드 추천 API는 인증 사용자와 예상 결제금액을 서비스에 전달한다")
    void returnsMerchantCardRecommendations() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn("user-1");
        when(merchantCardRecommendationService.recommend(
                "user-1", "merchant-1", new java.math.BigDecimal("25000"))).thenReturn(
                new MerchantCardRecommendationResponse(
                        new MerchantSummaryResponse("merchant-1", "이마트", "MART", "마트"),
                        BenefitPreferenceType.IMMEDIATE_SAVINGS, null, List.of()));

        JsonNode response = new ObjectMapper().readTree(mockMvc.perform(
                        get("/merchants/merchant-1/card-recommendations").param("paymentAmount", "25000"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertEquals("MART", response.path("data").path("merchant").path("categoryCode").asText());
    }

    @Test
    @DisplayName("가맹점 카드 추천 API는 잘못된 결제금액 형식을 400으로 거절한다")
    void rejectsInvalidRecommendationPaymentAmount() throws Exception {
        mockMvc.perform(get("/merchants/merchant-1/card-recommendations")
                        .param("paymentAmount", "invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("장소 카드 추천 API는 Kakao 분류와 인증 사용자를 서비스에 전달한다")
    void returnsPlaceCardRecommendations() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn("user-1");
        when(merchantCardRecommendationService.recommendPlace(
                "user-1", "동네카페", "CE7", "음식점 > 카페", null)).thenReturn(
                new MerchantCardRecommendationResponse(
                        new MerchantSummaryResponse(null, "동네카페", "CAFE", "카페"),
                        BenefitPreferenceType.IMMEDIATE_SAVINGS, null, List.of()));

        JsonNode response = new ObjectMapper().readTree(mockMvc.perform(
                        get("/merchants/place-card-recommendations")
                                .param("placeName", "동네카페")
                                .param("categoryGroupCode", "CE7")
                                .param("categoryName", "음식점 > 카페"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertEquals("CAFE", response.path("data").path("merchant").path("categoryCode").asText());
    }

    @Test
    @DisplayName("장소 카드 추천 API는 필수 Kakao 장소값이 없으면 400을 반환한다")
    void rejectsMissingPlaceRecommendationParameters() throws Exception {
        mockMvc.perform(get("/merchants/place-card-recommendations").param("placeName", "동네카페"))
                .andExpect(status().isBadRequest());
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
        when(merchantNearbyQueryService.getNearbyMerchants("cat-cafe", 37.5, 127.0, 500, null)).thenReturn(List.of(
                new NearbyMerchantResponse("m-1", "스타벅스", 37.501, 127.001, 120, "서울 강남구 테헤란로 1")));

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
        assertEquals("서울 강남구 테헤란로 1", response.path("data").get(0).path("address").asText());
    }

    @Test
    @DisplayName("근처 가맹점 API는 radiusMeters 생략을 허용한다")
    void allowsOmittingRadiusForNearbyMerchants() throws Exception {
        when(merchantNearbyQueryService.getNearbyMerchants("cat-cafe", 37.5, 127.0, null, null))
                .thenReturn(List.of());

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
        when(merchantNearbyQueryService.getNearbyMerchants("cat-unknown", 37.5, 127.0, 500, null))
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
    @DisplayName("근처 가맹점 API는 merchantId를 서비스에 전달한다")
    void passesMerchantIdForNearbyMerchants() throws Exception {
        when(merchantNearbyQueryService.getNearbyMerchants("cat-cafe", 37.5, 127.0, 500, "m-starbucks"))
                .thenReturn(List.of(new NearbyMerchantResponse(
                        "m-starbucks", "스타벅스 강남점", 37.501, 127.001, 120, "서울 강남구 테헤란로 1")));

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode response = objectMapper.readTree(mockMvc.perform(get("/merchants/nearby")
                        .param("categoryId", "cat-cafe")
                        .param("latitude", "37.5")
                        .param("longitude", "127.0")
                        .param("radiusMeters", "500")
                        .param("merchantId", "m-starbucks"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertEquals("스타벅스 강남점", response.path("data").get(0).path("name").asText());
    }

    @Test
    @DisplayName("merchantId가 그 카테고리에 없으면 404를 반환한다")
    void returnsNotFoundForUnknownMerchantId() throws Exception {
        when(merchantNearbyQueryService.getNearbyMerchants("cat-cafe", 37.5, 127.0, 500, "m-unknown"))
                .thenThrow(new MerchantNotFoundException(
                        "해당 카테고리에 존재하지 않는 가맹점입니다. categoryId=cat-cafe merchantId=m-unknown"));

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode response = objectMapper.readTree(mockMvc.perform(get("/merchants/nearby")
                        .param("categoryId", "cat-cafe")
                        .param("latitude", "37.5")
                        .param("longitude", "127.0")
                        .param("radiusMeters", "500")
                        .param("merchantId", "m-unknown"))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString());

        assertEquals("MERCHANT_NOT_FOUND", response.path("error").path("code").asText());
    }

    @Test
    @DisplayName("카카오맵 상류 장애면 503을 반환한다")
    void returnsServiceUnavailableWhenKakaoFails() throws Exception {
        when(merchantNearbyQueryService.getNearbyMerchants("cat-cafe", 37.5, 127.0, 500, null))
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
