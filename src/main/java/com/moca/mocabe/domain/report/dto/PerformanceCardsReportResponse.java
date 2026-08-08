package com.moca.mocabe.domain.report.dto;

import java.util.List;

/** 모든 활성 보유 카드의 월 실적 현황 리포트다. */
public record PerformanceCardsReportResponse(
    String yearMonth, List<PerformanceCardResponse> cards) { }
