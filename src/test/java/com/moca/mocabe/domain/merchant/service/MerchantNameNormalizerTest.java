package com.moca.mocabe.domain.merchant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MerchantNameNormalizerTest {

    private final MerchantNameNormalizer normalizer = new MerchantNameNormalizer();

    @Test
    @DisplayName("대소문자·공백·특수문자를 제거하고 한글은 보존한다")
    void normalizesName() {
        assertEquals("메가엠지씨MGC커피", normalizer.normalize(" 메가엠지씨(MGC) 커피 "));
        assertEquals("STARBUCKS", normalizer.normalize("star-bucks"));
    }

    @Test
    @DisplayName("null이면 빈 문자열로 정규화한다")
    void normalizesNullToEmpty() {
        assertEquals("", normalizer.normalize(null));
    }
}
