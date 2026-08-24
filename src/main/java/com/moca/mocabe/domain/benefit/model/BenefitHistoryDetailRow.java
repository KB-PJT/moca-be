package com.moca.mocabe.domain.benefit.model;

/** 혜택 상세 및 같은 월 한도 집계 SQL 조회 모델이다. */
public class BenefitHistoryDetailRow extends BenefitHistoryRow {
  private Long earnedMileage;

  public Long getEarnedMileage() {
    return earnedMileage;
  }

  public void setEarnedMileage(Long value) {
    earnedMileage = value;
  }
}
