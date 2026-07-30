package com.moca.mocabe.domain.user.dto;

import com.moca.mocabe.domain.user.model.LocationSettings;

/** 지도 기반 혜택 추천 설정 응답이다. */
public class LocationSettingsResponse {

    private final boolean locationRecommendationEnabled;

    public LocationSettingsResponse(LocationSettings settings) {
        this.locationRecommendationEnabled = settings.isLocationRecommendationEnabled();
    }

    public boolean isLocationRecommendationEnabled() {
        return locationRecommendationEnabled;
    }
}
