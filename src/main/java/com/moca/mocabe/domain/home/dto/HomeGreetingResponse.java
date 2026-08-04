package com.moca.mocabe.domain.home.dto;

/** 홈 상단 인사와 이번 달 놓친 혜택 요약이다. */
public class HomeGreetingResponse {

    private final String nickname;
    private final String yearMonth;
    private final long missedBenefitAmount;
    private final String message;

    public HomeGreetingResponse(String nickname, String yearMonth, long missedBenefitAmount, String message) {
        this.nickname = nickname;
        this.yearMonth = yearMonth;
        this.missedBenefitAmount = missedBenefitAmount;
        this.message = message;
    }

    public String getNickname() {
        return nickname;
    }

    public String getYearMonth() {
        return yearMonth;
    }

    public long getMissedBenefitAmount() {
        return missedBenefitAmount;
    }

    public String getMessage() {
        return message;
    }
}
