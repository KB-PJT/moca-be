package com.moca.mocabe.domain.merchant.dto;

public record NearbyMerchantResponse(
        String merchantId,
        String name,
        double latitude,
        double longitude,
        Integer distanceMeters,
        String address
) {
}
