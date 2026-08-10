package com.moca.mocabe.domain.benefit.model;

/** 혜택 내역 화면의 유형별 월 합계 SQL 조회 모델이다. */
public record BenefitHistorySummaryRow(
    long totalBenefitAmount,
    long discountAmount,
    long cashbackAmount,
    long pointAmount,
    long mileageAmount) { }
