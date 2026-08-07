package com.moca.mocabe.domain.merchant.model;

/** 카카오맵 로컬 API 검색 결과 한 건이다. distanceMeters는 x,y(중심좌표) 전달 시에만 채워진다. */
public record KakaoPlace(
        String placeName,
        double latitude,
        double longitude,
        Integer distanceMeters
) {
}
