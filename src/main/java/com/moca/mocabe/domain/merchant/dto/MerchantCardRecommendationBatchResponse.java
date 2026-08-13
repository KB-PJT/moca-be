package com.moca.mocabe.domain.merchant.dto;

import java.util.List;

/** 지도 목록에 결합할 가맹점별 카드 추천 요약 목록이다. */
public record MerchantCardRecommendationBatchResponse(
    List<MerchantCardRecommendationResponse> recommendations) { }
