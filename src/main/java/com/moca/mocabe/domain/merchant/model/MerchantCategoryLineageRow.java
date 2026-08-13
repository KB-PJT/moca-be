package com.moca.mocabe.domain.merchant.model;

/** 배치 추천에서 가맹점별 상위 카테고리를 한 번에 조회한 행이다. */
public record MerchantCategoryLineageRow(String merchantId, String merchantCategoryId) { }
