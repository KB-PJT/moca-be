package com.moca.mocabe.domain.merchant.model;

import java.math.BigDecimal;

/** 혜택 offer에 연결된 전체 실적별 월 한도 행이다. */
public record MerchantBenefitTierRow(
        String offerId, Integer position, BigDecimal requiredPreviousSpendKrw,
        BigDecimal monthlyLimitKrw) { }
