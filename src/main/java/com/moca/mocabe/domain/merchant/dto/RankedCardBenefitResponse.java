package com.moca.mocabe.domain.merchant.dto;

import java.math.BigDecimal;

public record RankedCardBenefitResponse(
        int rank, String userCardId, String cardName, String issuerName, String cardImageUrl,
        String benefitTitle, String rewardType, String rewardUnit, BigDecimal rewardValue,
        BigDecimal estimatedValueKrw, BigDecimal previousMonthSpendKrw,
        BigDecimal requiredPreviousSpendKrw, BigDecimal remainingPreviousSpendKrw,
        boolean performanceMet) { }
