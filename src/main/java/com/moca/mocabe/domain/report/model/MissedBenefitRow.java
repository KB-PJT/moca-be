package com.moca.mocabe.domain.report.model;

/** 실제 계산 예상액과 적용액의 차이로 확인된 미적용 혜택 행이다. */
public record MissedBenefitRow(
    String benefitRuleId, String title, String benefitType, long usedAmount, long limitAmount) { }
