package com.moca.mocabe.domain.report.dto;

import java.util.List;

/** 선택 옵션과 실적에 따라 제공된 월 한도 중 사용하지 않은 혜택을 제공하는 리포트다. */
public record MissedBenefitsReportResponse(
    String yearMonth,
    ReportUserCardResponse userCard,
    long totalMissedBenefitAmount,
    long approvalCount,
    long outcomeCount,
    long usageCount,
    List<MissedBenefitItemResponse> benefits) { }
