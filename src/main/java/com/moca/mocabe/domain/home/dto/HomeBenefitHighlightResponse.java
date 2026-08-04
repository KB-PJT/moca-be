package com.moca.mocabe.domain.home.dto;

/** 홈 카드에 표시할 대표 혜택 요약이다. */
public class HomeBenefitHighlightResponse {

    private final String title;
    private final String monthlyLimitText;

    public HomeBenefitHighlightResponse(String title, String monthlyLimitText) {
        this.title = title;
        this.monthlyLimitText = monthlyLimitText;
    }

    public String getTitle() {
        return title;
    }

    public String getMonthlyLimitText() {
        return monthlyLimitText;
    }
}
