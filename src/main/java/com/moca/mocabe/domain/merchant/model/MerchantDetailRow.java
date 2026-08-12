package com.moca.mocabe.domain.merchant.model;

public record MerchantDetailRow(
        String merchantId,
        String name,
        String merchantCategoryId,
        String categoryCode,
        String categoryName) {

    public MerchantDetailRow(String merchantId, String name, String categoryCode, String categoryName) {
        this(merchantId, name, null, categoryCode, categoryName);
    }
}
