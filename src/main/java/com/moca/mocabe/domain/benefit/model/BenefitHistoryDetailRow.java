package com.moca.mocabe.domain.benefit.model;

/** 혜택 상세 및 같은 월 한도 집계 SQL 조회 모델이다. */
public class BenefitHistoryDetailRow extends BenefitHistoryRow {
  private long monthlyUsedAmount;
  private long monthlyLimitAmount;
  private Long earnedMileage;

  public long getMonthlyUsedAmount() {
    return monthlyUsedAmount;
  }

  public void setMonthlyUsedAmount(long value) {
    monthlyUsedAmount = value;
  }

  public long getMonthlyLimitAmount() {
    return monthlyLimitAmount;
  }

  public void setMonthlyLimitAmount(long value) {
    monthlyLimitAmount = value;
  }

  public Long getEarnedMileage() {
    return earnedMileage;
  }

  public void setEarnedMileage(Long value) {
    earnedMileage = value;
  }
}
