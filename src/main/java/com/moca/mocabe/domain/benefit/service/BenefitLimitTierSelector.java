package com.moca.mocabe.domain.benefit.service;

import com.moca.mocabe.domain.benefit.model.BenefitLimitTierCandidate;
import com.moca.mocabe.domain.benefit.model.BenefitLimitTierSelection;
import com.moca.mocabe.domain.benefit.model.MonthlyBenefitLimit;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

/** 전월 자격과 당월 실적을 분리해 월 한도 tier를 선택한다. */
public class BenefitLimitTierSelector {
  public BenefitLimitTierSelection select(
      List<BenefitLimitTierCandidate> candidates,
      BigDecimal previousMonthSpend,
      BigDecimal currentMonthSpend) {
    return select(candidates, previousMonthSpend, currentMonthSpend, null);
  }

  public BenefitLimitTierSelection select(
      List<BenefitLimitTierCandidate> candidates,
      BigDecimal previousMonthSpend,
      BigDecimal currentMonthSpend,
      LocalDate usageDate) {
    if (candidates == null || candidates.isEmpty()) {
      return BenefitLimitTierSelection.noPolicy();
    }
    List<BenefitLimitTierCandidate> performanceEligible = candidates.stream()
        .filter(candidate -> appliesInMonth(candidate.applicableMonthsJson(), usageDate))
        .filter(candidate -> candidate.previousSpendMinKrw() == null
            || previousMonthSpend.compareTo(candidate.previousSpendMinKrw()) >= 0)
        .toList();
    if (performanceEligible.isEmpty()) {
      return BenefitLimitTierSelection.performanceNotMet();
    }
    return performanceEligible.stream()
        .filter(candidate -> candidate.currentSpendMinKrw() == null
            || currentMonthSpend.compareTo(candidate.currentSpendMinKrw()) >= 0)
        .max(Comparator.comparing(candidate -> candidate.currentSpendMinKrw() == null
            ? BigDecimal.ZERO : candidate.currentSpendMinKrw()))
        .map(candidate -> new MonthlyBenefitLimit(
            candidate.limitPolicyId(), candidate.sharedGroupKey(), candidate.limitValue()))
        .map(BenefitLimitTierSelection::selected)
        .orElseGet(BenefitLimitTierSelection::currentTierNotMet);
  }

  private boolean appliesInMonth(String applicableMonthsJson, LocalDate usageDate) {
    if (applicableMonthsJson == null || applicableMonthsJson.isBlank() || usageDate == null) {
      return true;
    }
    Set<Integer> months = java.util.Arrays.stream(applicableMonthsJson
            .replace('[', ' ').replace(']', ' ').trim().split(","))
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .map(Integer::parseInt)
        .collect(Collectors.toSet());
    return months.contains(usageDate.getMonthValue());
  }

}
