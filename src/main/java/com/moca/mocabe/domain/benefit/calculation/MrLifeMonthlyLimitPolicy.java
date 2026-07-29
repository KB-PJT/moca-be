package com.moca.mocabe.domain.benefit.calculation;

import java.math.BigDecimal;

import com.moca.mocabe.domain.benefit.model.BenefitCalculationContext;

/**
 * 신한카드 Mr.Life의 전월 실적 구간별 월 통합 한도를 결정한다.
 */
public class MrLifeMonthlyLimitPolicy {

    private static final BigDecimal FIRST_TIER_MINIMUM_SPEND = new BigDecimal("300000");
    private static final BigDecimal SECOND_TIER_MINIMUM_SPEND = new BigDecimal("500000");
    private static final BigDecimal THIRD_TIER_MINIMUM_SPEND = new BigDecimal("1000000");

    /**
     * 공과금 서비스 월 통합 한도다.
     */
    public BigDecimal utilityLimit(BenefitCalculationContext context) {
        return monthlyLimit(context, "3000", "7000", "10000");
    }

    /**
     * TIME 할인 서비스 월 통합 한도다.
     */
    public BigDecimal timeLimit(BenefitCalculationContext context) {
        return monthlyLimit(context, "10000", "20000", "30000");
    }

    /**
     * 주말 할인 서비스 월 통합 한도다.
     */
    public BigDecimal weekendLimit(BenefitCalculationContext context) {
        return monthlyLimit(context, "3000", "7000", "10000");
    }

    private BigDecimal monthlyLimit(BenefitCalculationContext context, String firstTierLimit,
            String secondTierLimit, String thirdTierLimit) {
        if (context.newMemberGracePeriod()) {
            return new BigDecimal(firstTierLimit);
        }
        if (context.previousMonthSpend().compareTo(FIRST_TIER_MINIMUM_SPEND) < 0) {
            return BigDecimal.ZERO;
        }
        if (context.previousMonthSpend().compareTo(SECOND_TIER_MINIMUM_SPEND) < 0) {
            return new BigDecimal(firstTierLimit);
        }
        if (context.previousMonthSpend().compareTo(THIRD_TIER_MINIMUM_SPEND) < 0) {
            return new BigDecimal(secondTierLimit);
        }
        return new BigDecimal(thirdTierLimit);
    }
}
