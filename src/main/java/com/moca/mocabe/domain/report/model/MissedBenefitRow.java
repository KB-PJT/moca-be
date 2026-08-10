package com.moca.mocabe.domain.report.model;

/** 선택 옵션과 실적에 따라 사용할 수 있었던 월 혜택 한도와 실제 사용액이다. */
public record MissedBenefitRow(
    String benefitRuleId, String title, String benefitType, long usedAmount, long limitAmount) { }
