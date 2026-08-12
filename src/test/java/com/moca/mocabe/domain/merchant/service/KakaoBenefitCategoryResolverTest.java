package com.moca.mocabe.domain.merchant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moca.mocabe.domain.merchant.model.KakaoCategoryResolutionRule;
import com.moca.mocabe.domain.merchant.model.KakaoPlace;
import com.moca.mocabe.domain.merchant.model.ResolvedKakaoCategory;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KakaoBenefitCategoryResolverTest {

    private final KakaoBenefitCategoryResolver resolver = new KakaoBenefitCategoryResolver();

    @Test
    @DisplayName("HP8 치과 상세 분류는 DENTAL로 계산한다")
    void resolvesDetailedMedicalCategory() {
        ResolvedKakaoCategory result = resolver.resolve(
                place("서울미소치과", "HP8", "의료,건강 > 병원 > 치과"),
                List.of(rule("DENTAL", "HP8", "치과", "GROUP_AND_PATTERN", "0.990", "ALLOW", 1)),
                decimal("0.900"));

        assertTrue(result.calculable());
        assertEquals("DENTAL", result.categoryCode());
    }

    @Test
    @DisplayName("HP8 상세 분류가 없으면 포괄 병원 혜택을 추정하지 않는다")
    void genericHospitalIsDisplayOnly() {
        ResolvedKakaoCategory result = resolver.resolve(
                place("서울병원", "HP8", "의료,건강 > 병원"),
                List.of(rule("DENTAL", "HP8", "치과", "GROUP_AND_PATTERN", "0.990", "ALLOW", 1)),
                decimal("0.800"));

        assertFalse(result.calculable());
    }

    @Test
    @DisplayName("PM9는 그룹코드만으로 약국을 계산한다")
    void resolvesPharmacyByGroupCode() {
        ResolvedKakaoCategory result = resolver.resolve(
                place("행복약국", "PM9", "의료,건강 > 약국"),
                List.of(rule("PHARMACY", "PM9", "", "GROUP_CODE", "1.000", "ALLOW", 1)),
                decimal("1.000"));

        assertTrue(result.calculable());
        assertEquals("PHARMACY", result.categoryCode());
    }

    @Test
    @DisplayName("DISPLAY_ONLY 또는 최소 신뢰도 미달은 계산하지 않는다")
    void rejectsDisplayOnlyAndLowConfidence() {
        KakaoPlace cafe = place("카페", "CE7", "음식점 > 카페");
        assertFalse(resolver.resolve(cafe,
                List.of(rule("CAFE", "CE7", "", "GROUP_CODE", "1.000", "DISPLAY_ONLY", 1)),
                decimal("0.800")).calculable());
        assertFalse(resolver.resolve(cafe,
                List.of(rule("CAFE", "CE7", "", "GROUP_CODE", "0.799", "ALLOW", 1)),
                decimal("0.800")).calculable());
    }

    @Test
    @DisplayName("OL7처럼 패턴이 필요한 그룹은 상세 카테고리가 맞아야 한다")
    void requiresPatternForAmbiguousGroup() {
        KakaoCategoryResolutionRule electric =
                rule("EV_CHARGING", "OL7", "전기차충전소", "GROUP_AND_PATTERN", "0.950", "ALLOW", 1);

        assertTrue(resolver.resolve(place("충전소", "OL7", "교통 > 전기차충전소"), List.of(electric),
                decimal("0.900")).calculable());
        assertFalse(resolver.resolve(place("주유소", "OL7", "교통 > 주유소"), List.of(electric),
                decimal("0.900")).calculable());
    }

    @Test
    @DisplayName("입력이나 규칙이 없으면 계산하지 않고 null 최소 신뢰도는 0으로 처리한다")
    void handlesMissingInputsAndNullThreshold() {
        assertFalse(resolver.resolve(null, List.of(), null).calculable());
        assertFalse(resolver.resolve(place("카페", "CE7", "음식점 > 카페"), null, null)
                .calculable());

        ResolvedKakaoCategory result = resolver.resolve(
                place("카페", " CE7 ", "음식점 > 카페"),
                List.of(rule("CAFE", "ce7", "", "GROUP_CODE", "0.000", "ALLOW", 1)),
                null);

        assertTrue(result.calculable());
    }

    @Test
    @DisplayName("NAME_PATTERN은 상세 카테고리 또는 장소명을 검사하고 알 수 없는 방식은 거부한다")
    void resolvesNamePatternAndRejectsUnknownMethod() {
        KakaoCategoryResolutionRule byName =
                rule("CAFE", "CE7", "스타벅스", "NAME_PATTERN", "0.900", "ALLOW", 1);

        assertTrue(resolver.resolve(place("스타벅스 강남점", "CE7", "음식점 > 카페"),
                List.of(byName), decimal("0.800")).calculable());
        assertTrue(resolver.resolve(place("강남점", "CE7", "음식점 > 카페 > 스타벅스"),
                List.of(byName), decimal("0.800")).calculable());
        assertFalse(resolver.resolve(place("스타벅스", "CE7", "음식점 > 카페"),
                List.of(rule("CAFE", "CE7", "스타벅스", "UNKNOWN", "1.000", "ALLOW", 1)),
                decimal("0.800")).calculable());
        assertFalse(resolver.resolve(place("스타벅스", null, "음식점 > 카페"),
                List.of(byName), decimal("0.800")).calculable());
    }

    private KakaoPlace place(String name, String groupCode, String categoryName) {
        return new KakaoPlace(name, 37.5, 127.0, 100, null, groupCode, categoryName);
    }

    private KakaoCategoryResolutionRule rule(String categoryCode, String groupCode, String pattern,
                                             String method, String confidence, String policy, int priority) {
        return new KakaoCategoryResolutionRule(categoryCode + "-id", categoryCode, groupCode, pattern,
                method, decimal(confidence), policy, priority);
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
