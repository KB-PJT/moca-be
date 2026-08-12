package com.moca.mocabe.domain.merchant.controller;

import com.moca.mocabe.domain.merchant.dto.MerchantCategoryResponse;
import com.moca.mocabe.domain.merchant.dto.MerchantResponse;
import com.moca.mocabe.domain.merchant.dto.NearbyMerchantResponse;
import com.moca.mocabe.domain.merchant.dto.MerchantCardRecommendationResponse;
import com.moca.mocabe.domain.merchant.service.MerchantCategoryQueryService;
import com.moca.mocabe.domain.merchant.service.MerchantNearbyQueryService;
import com.moca.mocabe.domain.merchant.service.MerchantQueryService;
import com.moca.mocabe.domain.merchant.service.MerchantCardRecommendationService;
import com.moca.mocabe.global.auth.CurrentUserProvider;
import java.math.BigDecimal;
import com.moca.mocabe.global.response.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 가맹점 카테고리·카테고리별 가맹점·근처 가맹점 조회 API를 제공한다. */
@RestController
@RequestMapping("/merchants")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantCategoryQueryService merchantCategoryQueryService;
    private final MerchantQueryService merchantQueryService;
    private final MerchantNearbyQueryService merchantNearbyQueryService;
    private final MerchantCardRecommendationService merchantCardRecommendationService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<MerchantCategoryResponse>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.success(merchantCategoryQueryService.getCategories()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<MerchantResponse>>> getMerchants(
            @RequestParam(name = "categoryId") String categoryId) {
        return ResponseEntity.ok(ApiResponse.success(merchantQueryService.getMerchantsByCategory(categoryId)));
    }

    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<List<NearbyMerchantResponse>>> getNearbyMerchants(
            @RequestParam(name = "categoryId") String categoryId,
            @RequestParam(name = "latitude") Double latitude,
            @RequestParam(name = "longitude") Double longitude,
            @RequestParam(name = "radiusMeters", required = false) Integer radiusMeters,
            @RequestParam(name = "merchantId", required = false) String merchantId) {
        return ResponseEntity.ok(ApiResponse.success(merchantNearbyQueryService.getNearbyMerchants(
                categoryId, latitude, longitude, radiusMeters, merchantId)));
    }

    @GetMapping("/{merchantId}/card-recommendations")
    public ResponseEntity<ApiResponse<MerchantCardRecommendationResponse>> getCardRecommendations(
            @org.springframework.web.bind.annotation.PathVariable String merchantId,
            @RequestParam(name = "paymentAmount", required = false) BigDecimal paymentAmount) {
        return ResponseEntity.ok(ApiResponse.success(merchantCardRecommendationService.recommend(
                currentUserProvider.getCurrentUserId(), merchantId, paymentAmount)));
    }

    @GetMapping("/place-card-recommendations")
    public ResponseEntity<ApiResponse<MerchantCardRecommendationResponse>> getPlaceCardRecommendations(
            @RequestParam(name = "placeName") String placeName,
            @RequestParam(name = "categoryGroupCode") String categoryGroupCode,
            @RequestParam(name = "categoryName", required = false) String categoryName,
            @RequestParam(name = "paymentAmount", required = false) BigDecimal paymentAmount) {
        return ResponseEntity.ok(ApiResponse.success(merchantCardRecommendationService.recommendPlace(
                currentUserProvider.getCurrentUserId(), placeName, categoryGroupCode,
                categoryName, paymentAmount)));
    }
}
