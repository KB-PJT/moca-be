package com.moca.mocabe.domain.merchant.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 보유 카드 혜택 룰과 대상 조건을 그대로 담는 조회 행이다. */
public record MerchantCardBenefitRuleRow(
        String merchantId, String merchantName, String categoryCode, String categoryName,
        String userCardId, String cardName, String issuerName, String cardImageUrl,
        String offerName, String rewardType, String rewardUnit, BigDecimal rewardValue,
        BigDecimal rewardBasisAmount, BigDecimal transactionMinKrw, BigDecimal previousSpendMinKrw,
        BigDecimal previousMonthSpendKrw, BigDecimal krwPerRewardUnit,
        BigDecimal monthlyLimitKrw, BigDecimal monthlyUsedKrw,
        String ruleId, String ruleEffect, LocalDate validFrom, LocalDate validTo,
        String matchMode, String targetType, String targetMerchantCategoryId, String targetMerchantId,
        BigDecimal minimumPlaceConfidence, boolean hasSchedule, boolean hasOptionRequirement) {

    public MerchantCardBenefitCandidate toCandidate() {
        return new MerchantCardBenefitCandidate(
                merchantId, merchantName, categoryCode, categoryName,
                userCardId, cardName, issuerName, cardImageUrl,
                offerName, rewardType, rewardUnit, rewardValue,
                rewardBasisAmount, transactionMinKrw, previousSpendMinKrw,
                previousMonthSpendKrw, krwPerRewardUnit, monthlyLimitKrw, monthlyUsedKrw);
    }
}
