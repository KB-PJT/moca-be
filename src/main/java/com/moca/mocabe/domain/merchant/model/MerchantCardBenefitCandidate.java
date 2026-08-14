package com.moca.mocabe.domain.merchant.model;

import java.math.BigDecimal;

/** 가맹점에 적용 가능한 보유 카드 혜택 룰 한 건이다. */
public record MerchantCardBenefitCandidate(
        String merchantId, String merchantName, String categoryCode, String categoryName,
        String userCardId, String cardName, String issuerName, String cardImageUrl,
        String offerName, String rewardType, String rewardUnit, BigDecimal rewardValue,
        BigDecimal rewardBasisAmount, BigDecimal transactionMinKrw, BigDecimal previousSpendMinKrw,
        BigDecimal previousMonthSpendKrw, BigDecimal krwPerRewardUnit,
        BigDecimal monthlyLimitKrw, BigDecimal monthlyUsedKrw, String offerId,
        Integer benefitTierPosition) {

    /** 기존 단일 룰 생성부와의 호환을 위한 생성자다. */
    public MerchantCardBenefitCandidate(
            String merchantId, String merchantName, String categoryCode, String categoryName,
            String userCardId, String cardName, String issuerName, String cardImageUrl,
            String offerName, String rewardType, String rewardUnit, BigDecimal rewardValue,
            BigDecimal rewardBasisAmount, BigDecimal transactionMinKrw, BigDecimal previousSpendMinKrw,
            BigDecimal previousMonthSpendKrw, BigDecimal krwPerRewardUnit,
            BigDecimal monthlyLimitKrw, BigDecimal monthlyUsedKrw) {
        this(merchantId, merchantName, categoryCode, categoryName, userCardId, cardName, issuerName,
                cardImageUrl, offerName, rewardType, rewardUnit, rewardValue, rewardBasisAmount,
                transactionMinKrw, previousSpendMinKrw, previousMonthSpendKrw, krwPerRewardUnit,
                monthlyLimitKrw, monthlyUsedKrw, null, null);
    }
}
