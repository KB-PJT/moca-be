package com.moca.mocabe.domain.merchant.model;

/** 특정 카테고리에 속한 활성 가맹점 한 건이다. */
public record MerchantListRow(
        String merchantId,
        String name
) {
}
