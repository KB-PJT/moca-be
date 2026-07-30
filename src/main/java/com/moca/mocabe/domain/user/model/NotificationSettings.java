package com.moca.mocabe.domain.user.model;

/** 사용자 단위 알림 설정 MyBatis 모델이다. */
public class NotificationSettings {

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
