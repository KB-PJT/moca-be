package com.moca.mocabe.domain.merchant.dto;

import java.math.BigDecimal;
import java.util.List;

public record RankedCardBenefitResponse(
        int rank, String userCardId, String cardName, String issuerName, String cardImageUrl,
        String benefitTitle, String rewardType, String rewardUnit, BigDecimal rewardValue,
        BigDecimal estimatedValueKrw, BigDecimal estimatedPaymentAmountKrw,
        BigDecimal transactionMinKrw, BigDecimal previousMonthSpendKrw,
        BigDecimal requiredPreviousSpendKrw, BigDecimal remainingPreviousSpendKrw,
        Integer currentTier, Integer nextTier, BigDecimal currentTierTargetAmount,
        boolean isCurrentTierAchieved, BigDecimal remainingAmountToNextTier,
        BigDecimal monthlyLimitKrw, BigDecimal monthlyUsedKrw, BigDecimal monthlyRemainingKrw,
        boolean performanceMet, List<RecommendationReasonResponse> recommendationReasons,
        List<BenefitTierResponse> tiers) { }
