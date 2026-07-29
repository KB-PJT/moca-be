package com.moca.mocabe.domain.benefit.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 특정 결제 상황에서 혜택을 계산하기 위한 입력값이다.
 */
public record BenefitCalculationContext(
        BigDecimal paymentAmount,
        BigDecimal usageQuantity,
        BigDecimal previousMonthSpend,
        LocalDateTime approvedAt,
        String mocaCategory,
        boolean newMemberGracePeriod,
        int usedDailyCount,
        int usedMonthlyCount,
        boolean merchantEligible,
        boolean paymentChannelEligible
) {

    public BenefitCalculationContext(BigDecimal paymentAmount, BigDecimal usageQuantity,
            BigDecimal previousMonthSpend, LocalDateTime approvedAt, String mocaCategory) {
        this(paymentAmount, usageQuantity, previousMonthSpend, approvedAt, mocaCategory, false, 0, 0, true, true);
    }

    public BenefitCalculationContext {
        // 계산 입력이 비어도 BigDecimal 비교와 연산이 안전하게 동작하도록 0으로 보정한다.
        paymentAmount = paymentAmount == null ? BigDecimal.ZERO : paymentAmount;
        usageQuantity = usageQuantity == null ? BigDecimal.ZERO : usageQuantity;
        previousMonthSpend = previousMonthSpend == null ? BigDecimal.ZERO : previousMonthSpend;
        usedDailyCount = Math.max(usedDailyCount, 0);
        usedMonthlyCount = Math.max(usedMonthlyCount, 0);
    }
}
