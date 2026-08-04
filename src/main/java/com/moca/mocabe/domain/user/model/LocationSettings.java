package com.moca.mocabe.domain.user.model;

/** 지도 기반 혜택 추천 설정 MyBatis 모델이다. */
public class LocationSettings {

    private boolean locationRecommendationEnabled;

    public boolean isLocationRecommendationEnabled() {
        return locationRecommendationEnabled;
    }

    public void setLocationRecommendationEnabled(boolean locationRecommendationEnabled) {
        this.locationRecommendationEnabled = locationRecommendationEnabled;
    }
}
