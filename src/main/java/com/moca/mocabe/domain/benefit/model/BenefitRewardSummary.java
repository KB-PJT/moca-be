package com.moca.mocabe.domain.benefit.model;

import java.math.BigDecimal;

/**
 * CODEF 승인내역을 룰 기반으로 역산한 월별 혜택 합계다.
 */
public record BenefitRewardSummary(
        BigDecimal discountAmount,
        BigDecimal cashbackAmount,
        BigDecimal pointAmount,
        BigDecimal mileageAmount
) {
}
