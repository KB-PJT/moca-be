package com.moca.mocabe.domain.user.dto;

import javax.validation.constraints.NotNull;

/** 알림 토글을 한 번에 저장하는 요청이다. */
public class NotificationSettingsRequest {

    @NotNull(message = "실적 마감 알림 여부는 필수입니다.")
    private Boolean performanceClosingEnabled;

    @NotNull(message = "주변 혜택 알림 여부는 필수입니다.")
    private Boolean nearbyBenefitEnabled;

    @NotNull(message = "혜택 한도 알림 여부는 필수입니다.")
    private Boolean benefitLimitEnabled;

    @NotNull(message = "마케팅 알림 여부는 필수입니다.")
    private Boolean marketingEnabled;

    public Boolean getPerformanceClosingEnabled() {
        return performanceClosingEnabled;
    }

    public void setPerformanceClosingEnabled(Boolean performanceClosingEnabled) {
        this.performanceClosingEnabled = performanceClosingEnabled;
    }

    public Boolean getNearbyBenefitEnabled() {
        return nearbyBenefitEnabled;
    }

    public void setNearbyBenefitEnabled(Boolean nearbyBenefitEnabled) {
        this.nearbyBenefitEnabled = nearbyBenefitEnabled;
    }

    public Boolean getBenefitLimitEnabled() {
        return benefitLimitEnabled;
    }

    public void setBenefitLimitEnabled(Boolean benefitLimitEnabled) {
        this.benefitLimitEnabled = benefitLimitEnabled;
    }

    public Boolean getMarketingEnabled() {
        return marketingEnabled;
    }

    public void setMarketingEnabled(Boolean marketingEnabled) {
        this.marketingEnabled = marketingEnabled;
    }
}
