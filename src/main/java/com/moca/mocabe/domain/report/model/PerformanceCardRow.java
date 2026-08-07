package com.moca.mocabe.domain.report.model;

/** 사용자 카드별 지정 월 실적 조회 행이다. */
public record PerformanceCardRow(String userCardId, String cardName, String cardImageUrl,
                                 long currentPerformanceAmount, long currentTierTargetAmount,
                                 int currentTier, Integer nextTier, int displayOrder) {

    /** tier 데이터가 없는 기존 fixture와 콘텐츠에도 호환되는 기본 행이다. */
    public PerformanceCardRow(String userCardId, String cardName, String cardImageUrl,
                              long currentPerformanceAmount, long currentTierTargetAmount,
                              int displayOrder) {
        this(userCardId, cardName, cardImageUrl, currentPerformanceAmount, currentTierTargetAmount,
                0, null, displayOrder);
    }
}
