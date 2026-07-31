package com.moca.mocabe.domain.user.dto;

import com.moca.mocabe.domain.user.model.NotificationSettings;

/** 알림 설정 응답이다. */
public class NotificationSettingsResponse {

    private final boolean performanceClosingEnabled;
    private final boolean nearbyBenefitEnabled;
    private final boolean benefitLimitEnabled;
    private final boolean marketingEnabled;

    public NotificationSettingsResponse(NotificationSettings settings) {
        this.performanceClosingEnabled = settings.isPerformanceClosingEnabled();
        this.nearbyBenefitEnabled = settings.isNearbyBenefitEnabled();
        this.benefitLimitEnabled = settings.isBenefitLimitEnabled();
        this.marketingEnabled = settings.isMarketingEnabled();
    }

    public boolean isPerformanceClosingEnabled() {
        return performanceClosingEnabled;
    }

    public boolean isNearbyBenefitEnabled() {
        return nearbyBenefitEnabled;
    }

    public boolean isBenefitLimitEnabled() {
        return benefitLimitEnabled;
    }

    public boolean isMarketingEnabled() {
        return marketingEnabled;
    }
}
