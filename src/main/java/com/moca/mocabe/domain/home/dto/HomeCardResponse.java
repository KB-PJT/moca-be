package com.moca.mocabe.domain.home.dto;

/** 홈 카드 캐러셀에 표시할 카드 한 장이다. */
public class HomeCardResponse {

    private final String userCardId;
    private final int order;
    private final String cardName;
    private final String alias;
    private final String cardImageUrl;
    private final String autoOrderReason;
    private final HomeBenefitHighlightResponse highlightBenefit;
    private final HomeCardSummaryResponse summary;

    public HomeCardResponse(String userCardId, int order, String cardName, String alias,
                            String cardImageUrl, String autoOrderReason,
                            HomeBenefitHighlightResponse highlightBenefit,
                            HomeCardSummaryResponse summary) {
        this.userCardId = userCardId;
        this.order = order;
        this.cardName = cardName;
        this.alias = alias;
        this.cardImageUrl = cardImageUrl;
        this.autoOrderReason = autoOrderReason;
        this.highlightBenefit = highlightBenefit;
        this.summary = summary;
    }

    public String getUserCardId() {
        return userCardId;
    }

    public int getOrder() {
        return order;
    }

    public String getCardName() {
        return cardName;
    }

    public String getAlias() {
        return alias;
    }

    public String getCardImageUrl() {
        return cardImageUrl;
    }

    public String getAutoOrderReason() {
        return autoOrderReason;
    }

    public HomeBenefitHighlightResponse getHighlightBenefit() {
        return highlightBenefit;
    }

    public HomeCardSummaryResponse getSummary() {
        return summary;
    }
}
