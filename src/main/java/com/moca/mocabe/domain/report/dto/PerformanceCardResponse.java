package com.moca.mocabe.domain.report.dto;

/** 카드별 실적 현황 응답이다. 현재 스키마에는 실적 tier가 없어 0/null로 반환한다. */
public record PerformanceCardResponse(String userCardId, String cardName, String cardImageUrl,
                                      long currentPerformanceAmount, long currentTierTargetAmount,
                                      int achievementRate, int currentTier, Integer nextTier,
                                      boolean isCurrentTierAchieved, long remainingAmountToNextTier) {
}
