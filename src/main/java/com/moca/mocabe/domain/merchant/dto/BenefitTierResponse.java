package com.moca.mocabe.domain.merchant.dto;

import java.math.BigDecimal;

/** 추천 혜택의 전체 실적 구간 메타데이터다. */
public record BenefitTierResponse(
        Integer tier, BigDecimal requiredPreviousSpendKrw, BigDecimal monthlyLimitKrw) { }
