package com.moca.mocabe.domain.merchant.dto;

import java.math.BigDecimal;

/** 추천 근거를 프론트가 문구로 조합할 수 있도록 구조화한 항목이다. */
public record RecommendationReasonResponse(
    String code, boolean satisfied, BigDecimal currentValue, BigDecimal requiredValue,
    BigDecimal remainingValue) { }
