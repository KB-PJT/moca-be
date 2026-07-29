package com.moca.mocabe.domain.benefit.model;

import java.math.BigDecimal;

import com.moca.mocabe.domain.benefit.type.BenefitRejectionReason;
import com.moca.mocabe.domain.benefit.type.BenefitType;
import com.moca.mocabe.domain.benefit.type.RewardUnit;

/**
 * 혜택 룰 계산 결과다.
 */
public record BenefitCalculationResult(
        String ruleId,
        BenefitType benefitType,
        RewardUnit rewardUnit,
        boolean applicable,
        // 월 한도 적용 전 예상 혜택값이다.
        BigDecimal rawRewardValue,
        // 월 한도와 사용량을 반영해 실제 적용 가능한 혜택값이다.
        BigDecimal appliedRewardValue,
        BigDecimal remainingLimitValue,
        BenefitRejectionReason rejectionReason
) {
}
