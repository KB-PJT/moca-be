package com.moca.mocabe.domain.benefit.service;

import com.moca.mocabe.domain.benefit.model.BenefitLimitTierCandidate;
import com.moca.mocabe.domain.benefit.model.MonthlyBenefitLimit;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/** 전월 자격과 당월 실적을 분리해 월 한도 tier를 선택한다. */
public class BenefitLimitTierSelector {
  public MonthlyBenefitLimit select(
      List<BenefitLimitTierCandidate> candidates,
      BigDecimal previousMonthSpend,
      BigDecimal currentMonthSpend) {
    return candidates.stream()
        .filter(candidate -> candidate.previousSpendMinKrw() == null
            || previousMonthSpend.compareTo(candidate.previousSpendMinKrw()) >= 0)
        .filter(candidate -> candidate.currentSpendMinKrw() == null
            || currentMonthSpend.compareTo(candidate.currentSpendMinKrw()) >= 0)
        .max(Comparator.comparing(candidate -> candidate.currentSpendMinKrw() == null
            ? BigDecimal.ZERO : candidate.currentSpendMinKrw()))
        .map(candidate -> new MonthlyBenefitLimit(
            candidate.limitPolicyId(), candidate.sharedGroupKey(), candidate.limitValue()))
        .orElse(null);
  }
}
