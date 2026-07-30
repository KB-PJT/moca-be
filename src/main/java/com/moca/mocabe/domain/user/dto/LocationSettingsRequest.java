package com.moca.mocabe.domain.user.dto;

/** 지도 기반 혜택 추천 사용 여부 변경 요청이다. */
public class LocationSettingsRequest {

    private boolean locationRecommendationEnabled;

    public boolean isLocationRecommendationEnabled() {
        return locationRecommendationEnabled;
    }

    public void setLocationRecommendationEnabled(boolean locationRecommendationEnabled) {
        this.locationRecommendationEnabled = locationRecommendationEnabled;
    }
}
