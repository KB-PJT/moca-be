package com.moca.mocabe.domain.user.dto;

/** 알림 토글을 한 번에 저장하는 요청이다. */
public class NotificationSettingsRequest {

    private boolean performanceClosingEnabled;
    private boolean nearbyBenefitEnabled;
    private boolean benefitLimitEnabled;
    private boolean marketingEnabled;

    public boolean isPerformanceClosingEnabled() {
        return performanceClosingEnabled;
    }

    public void setPerformanceClosingEnabled(boolean performanceClosingEnabled) {
        this.performanceClosingEnabled = performanceClosingEnabled;
    }

    public boolean isNearbyBenefitEnabled() {
        return nearbyBenefitEnabled;
    }

    public void setNearbyBenefitEnabled(boolean nearbyBenefitEnabled) {
        this.nearbyBenefitEnabled = nearbyBenefitEnabled;
    }

    public boolean isBenefitLimitEnabled() {
        return benefitLimitEnabled;
    }

    public void setBenefitLimitEnabled(boolean benefitLimitEnabled) {
        this.benefitLimitEnabled = benefitLimitEnabled;
    }

    public boolean isMarketingEnabled() {
        return marketingEnabled;
    }

    public void setMarketingEnabled(boolean marketingEnabled) {
        this.marketingEnabled = marketingEnabled;
    }
}
