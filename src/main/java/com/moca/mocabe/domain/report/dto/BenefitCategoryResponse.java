package com.moca.mocabe.domain.report.dto;

/** 혜택 금액 기준 카테고리 순위다. */
public record BenefitCategoryResponse(
    int rank, String categoryCode, String categoryName, long benefitAmount) { }
