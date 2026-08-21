package com.moca.mocabe.domain.benefit.model;

import java.math.BigDecimal;

/** 월 한도 정책에서 계산 서비스가 선택할 수 있는 tier 후보다. */
public record BenefitLimitTierCandidate(
    String limitPolicyId,
    String sharedGroupKey,
    BigDecimal limitValue,
    BigDecimal previousSpendMinKrw,
    BigDecimal currentSpendMinKrw,
    String applicableMonthsJson) {

  public BenefitLimitTierCandidate(
      String limitPolicyId,
      String sharedGroupKey,
      BigDecimal limitValue,
      BigDecimal previousSpendMinKrw,
      BigDecimal currentSpendMinKrw) {
    this(limitPolicyId, sharedGroupKey, limitValue, previousSpendMinKrw, currentSpendMinKrw, null);
  }
}
