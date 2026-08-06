package com.moca.mocabe.domain.merchant.model;

/** merchant_categories 한 행이다. */
public record MerchantCategoryRow(
        String merchantCategoryId,
        String categoryCode,
        String categoryName,
        int displayOrder
) {
}
