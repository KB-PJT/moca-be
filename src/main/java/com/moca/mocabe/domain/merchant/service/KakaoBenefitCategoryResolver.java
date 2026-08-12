package com.moca.mocabe.domain.merchant.service;

import com.moca.mocabe.domain.merchant.model.KakaoCategoryResolutionRule;
import com.moca.mocabe.domain.merchant.model.KakaoPlace;
import com.moca.mocabe.domain.merchant.model.ResolvedKakaoCategory;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/** Kakao 외부 분류를 보수적으로 내부 혜택 카테고리로 변환한다. */
public class KakaoBenefitCategoryResolver {

    public ResolvedKakaoCategory resolve(KakaoPlace place, List<KakaoCategoryResolutionRule> rules,
                                         BigDecimal minimumConfidence) {
        if (place == null || rules == null || rules.isEmpty()) {
            return ResolvedKakaoCategory.displayOnly();
        }
        BigDecimal threshold = minimumConfidence == null ? BigDecimal.ZERO : minimumConfidence;
        return rules.stream()
                .filter(rule -> same(rule.kakaoCategoryGroupCode(), place.categoryGroupCode()))
                .filter(rule -> matchesMethod(rule, place))
                .sorted(Comparator.comparingInt(KakaoCategoryResolutionRule::priority))
                .findFirst()
                .filter(rule -> "ALLOW".equals(rule.benefitMatchPolicy()))
                .filter(rule -> rule.confidenceScore().compareTo(threshold) >= 0)
                .map(rule -> new ResolvedKakaoCategory(rule.merchantCategoryId(), rule.categoryCode(),
                        rule.confidenceScore(), true))
                .orElseGet(ResolvedKakaoCategory::displayOnly);
    }

    private boolean matchesMethod(KakaoCategoryResolutionRule rule, KakaoPlace place) {
        if ("GROUP_CODE".equals(rule.matchMethod())) {
            return true;
        }
        if ("GROUP_AND_PATTERN".equals(rule.matchMethod())) {
            return contains(place.categoryName(), rule.categoryNamePattern());
        }
        if ("NAME_PATTERN".equals(rule.matchMethod())) {
            return contains(place.categoryName(), rule.categoryNamePattern())
                    || contains(place.placeName(), rule.categoryNamePattern());
        }
        return false;
    }

    private boolean same(String left, String right) {
        return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
    }

    private boolean contains(String source, String pattern) {
        return source != null && pattern != null && !pattern.isBlank() && source.contains(pattern.trim());
    }
}
