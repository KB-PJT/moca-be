package com.moca.mocabe.domain.benefit.model;

/** 월 한도 정책의 존재, 전월 자격, 당월 tier 선택 결과를 구분한다. */
public record BenefitLimitTierSelection(Status status, MonthlyBenefitLimit limit) {
  public enum Status {
    NO_POLICY,
    PERFORMANCE_NOT_MET,
    CURRENT_TIER_NOT_MET,
    SELECTED
  }

  public static BenefitLimitTierSelection noPolicy() {
    return new BenefitLimitTierSelection(Status.NO_POLICY, null);
  }

  public static BenefitLimitTierSelection performanceNotMet() {
    return new BenefitLimitTierSelection(Status.PERFORMANCE_NOT_MET, null);
  }

  public static BenefitLimitTierSelection currentTierNotMet() {
    return new BenefitLimitTierSelection(Status.CURRENT_TIER_NOT_MET, null);
  }

  public static BenefitLimitTierSelection selected(MonthlyBenefitLimit limit) {
    return new BenefitLimitTierSelection(Status.SELECTED, limit);
  }
}
