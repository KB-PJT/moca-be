package com.moca.mocabe.domain.merchant.dto;

import java.math.BigDecimal;

/** 추천 카드의 예상 혜택 합계에 포함된 개별 혜택이다. */
public record AppliedCardBenefitResponse(
        String benefitTitle, String rewardType, String rewardUnit, BigDecimal rewardValue,
        BigDecimal estimatedValueKrw) { }
