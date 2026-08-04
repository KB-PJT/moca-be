package com.moca.mocabe.domain.home.dto;

/** 홈 카드 한 장의 당월 혜택·실적 요약이다. */
public class HomeCardSummaryResponse {

    private final long receivedBenefitAmount;
    private final long availableBenefitAmount;
    private final long maximumMonthlyBenefitAmount;
    private final long performanceCurrentAmount;
    private final long performanceTargetAmount;
    private final int performanceRate;
    private final long performanceRemainingAmount;

    public HomeCardSummaryResponse(long receivedBenefitAmount, long availableBenefitAmount,
                                   long maximumMonthlyBenefitAmount, long performanceCurrentAmount,
                                   long performanceTargetAmount, int performanceRate,
                                   long performanceRemainingAmount) {
        this.receivedBenefitAmount = receivedBenefitAmount;
        this.availableBenefitAmount = availableBenefitAmount;
        this.maximumMonthlyBenefitAmount = maximumMonthlyBenefitAmount;
        this.performanceCurrentAmount = performanceCurrentAmount;
        this.performanceTargetAmount = performanceTargetAmount;
        this.performanceRate = performanceRate;
        this.performanceRemainingAmount = performanceRemainingAmount;
    }

    public long getReceivedBenefitAmount() {
        return receivedBenefitAmount;
    }

    public long getAvailableBenefitAmount() {
        return availableBenefitAmount;
    }

    public long getMaximumMonthlyBenefitAmount() {
        return maximumMonthlyBenefitAmount;
    }

    public long getPerformanceCurrentAmount() {
        return performanceCurrentAmount;
    }

    public long getPerformanceTargetAmount() {
        return performanceTargetAmount;
    }

    public int getPerformanceRate() {
        return performanceRate;
    }

    public long getPerformanceRemainingAmount() {
        return performanceRemainingAmount;
    }
}
