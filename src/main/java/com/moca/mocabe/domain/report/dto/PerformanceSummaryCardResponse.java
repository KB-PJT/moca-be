package com.moca.mocabe.domain.report.dto;

/** 실적 상단 요약에 표시하는 카드다. */
public record PerformanceSummaryCardResponse(
    String userCardId, String cardName, int achievementRate, boolean isCurrentTierAchieved) { }
