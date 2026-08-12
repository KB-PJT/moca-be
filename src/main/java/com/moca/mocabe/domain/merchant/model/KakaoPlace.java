package com.moca.mocabe.domain.merchant.model;

/**
 * 카카오맵 로컬 API 검색 결과 한 건이다. distanceMeters는 x,y(중심좌표) 전달 시에만 채워진다.
 * address는 도로명 주소가 있으면 그 값, 없으면 지번 주소다.
 */
public record KakaoPlace(
        String placeName,
        double latitude,
        double longitude,
        Integer distanceMeters,
        String address,
        String categoryGroupCode,
        String categoryName
) {
    public KakaoPlace(String placeName, double latitude, double longitude,
                      Integer distanceMeters, String address) {
        this(placeName, latitude, longitude, distanceMeters, address, null, null);
    }
}
