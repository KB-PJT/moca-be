package com.moca.mocabe.domain.report.model;

/** 선택 옵션과 실적에 따라 사용할 수 있었던 월 혜택 한도와 실제 사용액이다. */
public record MissedBenefitRow(
    String benefitRuleId,
    String reportTitle,
    String benefitTitle,
    String ruleTitle,
    String offerName,
    String benefitType,
    long usedAmount,
    long limitAmount) {

  public MissedBenefitRow(
      String benefitRuleId, String title, String benefitType, long usedAmount, long limitAmount) {
    this(benefitRuleId, null, title, null, null, benefitType, usedAmount, limitAmount);
  }

  /** 기존 집계 테스트와 호환되는 원천 혜택 제목 접근자다. */
  public String title() {
    return benefitTitle;
  }
}
