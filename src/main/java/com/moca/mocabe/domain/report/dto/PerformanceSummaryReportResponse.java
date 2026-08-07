package com.moca.mocabe.domain.report.dto;

import java.util.List;

/** 실적 상단 도넛과 대표 카드 세 장의 리포트다. */
public record PerformanceSummaryReportResponse(
    String yearMonth,
    int cardCount,
    int achievedCardCount,
    List<PerformanceSummaryCardResponse> cards) { }
