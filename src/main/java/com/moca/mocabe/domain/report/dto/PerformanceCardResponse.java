package com.moca.mocabe.domain.report.dto;

import java.util.List;

/** 카드별 실적 현황과 현재·다음 실적 구간 진행 상태 응답이다. */
public record PerformanceCardResponse(
    String userCardId,
    String cardName,
    String cardImageUrl,
    long currentPerformanceAmount,
    long currentTierTargetAmount,
    int achievementRate,
    int currentTier,
    Integer nextTier,
    boolean isCurrentTierAchieved,
    long remainingAmountToNextTier,
    Long nextTierTargetAmount,
    List<PerformanceTierResponse> tiers) { }
