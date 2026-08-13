package com.moca.mocabe.domain.merchant.model;

import java.math.BigDecimal;

/** 가맹점에 적용 가능한 보유 카드 혜택 룰 한 건이다. */
public record MerchantCardBenefitCandidate(
        String merchantId, String merchantName, String categoryCode, String categoryName,
        String userCardId, String cardName, String issuerName, String cardImageUrl,
        String offerName, String rewardType, String rewardUnit, BigDecimal rewardValue,
        BigDecimal rewardBasisAmount, BigDecimal transactionMinKrw, BigDecimal previousSpendMinKrw,
        BigDecimal previousMonthSpendKrw, BigDecimal krwPerRewardUnit,
        BigDecimal monthlyLimitKrw, BigDecimal monthlyUsedKrw) { }
