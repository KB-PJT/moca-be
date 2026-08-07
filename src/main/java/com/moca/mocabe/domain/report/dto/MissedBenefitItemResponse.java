package com.moca.mocabe.domain.report.dto;

/** 계산된 예상 혜택(limitAmount) 중 실제 적용되지 못한 금액(remainingAmount)이다. */
public record MissedBenefitItemResponse(
    String benefitRuleId,
    String title,
    String type,
    long usedAmount,
    long limitAmount,
    long remainingAmount,
    String unit) { }
