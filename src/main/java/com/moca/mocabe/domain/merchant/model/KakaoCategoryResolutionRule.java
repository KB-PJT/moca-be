package com.moca.mocabe.domain.merchant.model;

import java.math.BigDecimal;

/** DB에 등록된 Kakao 외부 카테고리와 내부 가맹점 카테고리의 판정 규칙이다. */
public record KakaoCategoryResolutionRule(
        String merchantCategoryId,
        String categoryCode,
        String kakaoCategoryGroupCode,
        String categoryNamePattern,
        String matchMethod,
        BigDecimal confidenceScore,
        String benefitMatchPolicy,
        int priority) { }
