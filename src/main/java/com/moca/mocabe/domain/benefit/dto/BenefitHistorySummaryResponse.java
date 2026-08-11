package com.moca.mocabe.domain.benefit.dto;

/** 조회 월과 카드 조건에 해당하는 혜택 유형별 합계다. */
public record BenefitHistorySummaryResponse(
    long totalBenefitAmount,
    long discountAmount,
    long cashbackAmount,
    long pointAmount,
    long mileageAmount) { }
