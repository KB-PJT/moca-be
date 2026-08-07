package com.moca.mocabe.domain.report.dto;

import java.util.List;

/** 계산 결과에서 확인된 실제 미적용 혜택을 제공하는 리포트다. */
public record MissedBenefitsReportResponse(
    String yearMonth,
    ReportUserCardResponse userCard,
    long totalMissedBenefitAmount,
    List<MissedBenefitItemResponse> benefits) { }
