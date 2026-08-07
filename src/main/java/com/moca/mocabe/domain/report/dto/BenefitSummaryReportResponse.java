package com.moca.mocabe.domain.report.dto;

import java.util.List;

/** 월별 전체 혜택과 전월 비교 리포트다. */
public record BenefitSummaryReportResponse(
    String yearMonth,
    long totalBenefitAmount,
    long previousMonthBenefitAmount,
    long differenceAmount,
    List<BenefitBreakdownResponse> breakdown) { }
