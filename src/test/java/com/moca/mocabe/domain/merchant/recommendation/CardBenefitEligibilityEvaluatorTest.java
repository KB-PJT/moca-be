package com.moca.mocabe.domain.merchant.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.moca.mocabe.domain.merchant.model.MerchantCardBenefitRuleRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("가맹점 카드 혜택 적용 가능성 판정")
class CardBenefitEligibilityEvaluatorTest {
    private static final LocalDate USAGE_DATE = LocalDate.of(2026, 8, 12);
    private final CardBenefitEligibilityEvaluator evaluator = new CardBenefitEligibilityEvaluator();

    @Test
    @DisplayName("하위 카테고리는 상위 카테고리 혜택을 신뢰도 경계부터 적용한다")
    void appliesParentCategoryFromMinimumConfidence() {
        MerchantCardBenefitRuleRow row = row(
                "rule-1", "include", "merchant_category", "parent-category", null,
                new BigDecimal("0.850"), "grant", "discount", "percent",
                BigDecimal.TEN, null, null, false, false);

        assertEquals(1, evaluate(List.of(row), null,
                List.of("child-category", "parent-category"), "0.850").size());
        assertEquals(0, evaluate(List.of(row), null,
                List.of("child-category", "parent-category"), "0.849").size());
    }

    @Test
    @DisplayName("정확한 가맹점 include가 있어도 일치하는 exclude가 있으면 제외한다")
    void rejectsMatchingMerchantExclusion() {
        List<MerchantCardBenefitRuleRow> rows = List.of(
                row("rule-1", "include", "merchant", null, "merchant-1",
                        BigDecimal.ZERO, "grant", "cashback", "KRW",
                        BigDecimal.ONE, null, null, false, false),
                row("rule-1", "exclude", "merchant", null, "merchant-1",
                        BigDecimal.ZERO, "grant", "cashback", "KRW",
                        BigDecimal.ONE, null, null, false, false));

        assertEquals(0, evaluate(rows, "merchant-1", List.of(), null).size());
        assertEquals(0, evaluate(rows, "merchant-2", List.of(), null).size());
    }

    @Test
    @DisplayName("전체 가맹점 include는 적용하되 카테고리 exclude는 신뢰도와 무관하게 제외한다")
    void appliesAllMerchantsAndRejectsCategoryExclusion() {
        List<MerchantCardBenefitRuleRow> rows = List.of(
                row("rule-1", "include", "all_merchants", null, null,
                        BigDecimal.ZERO, "grant", "points", "point",
                        BigDecimal.ONE, null, null, false, false),
                row("rule-1", "exclude", "merchant_category", "category-1", null,
                        BigDecimal.ONE, "grant", "points", "point",
                        BigDecimal.ONE, null, null, false, false));

        assertEquals(0, evaluate(rows, null, List.of("category-1"), "0.100").size());
        assertEquals(1, evaluate(rows, null, List.of("category-2"), "0.100").size());
    }

    @Test
    @DisplayName("유효 시작일과 종료일은 포함하고 기간 밖의 룰은 제외한다")
    void appliesInclusiveValidityDates() {
        MerchantCardBenefitRuleRow active = row(
                "active", "include", "all_merchants", null, null, BigDecimal.ZERO,
                "grant", "rebate", "mile", BigDecimal.ONE, USAGE_DATE, USAGE_DATE,
                false, false);
        MerchantCardBenefitRuleRow future = row(
                "future", "include", "all_merchants", null, null, BigDecimal.ZERO,
                "grant", "rebate", "mile", BigDecimal.ONE, USAGE_DATE.plusDays(1), null,
                false, false);
        MerchantCardBenefitRuleRow expired = row(
                "expired", "include", "all_merchants", null, null, BigDecimal.ZERO,
                "grant", "rebate", "mile", BigDecimal.ONE, null, USAGE_DATE.minusDays(1),
                false, false);

        assertEquals(1, evaluate(List.of(active, future, expired), null, List.of(), null).size());
    }

    @Test
    @DisplayName("일정·옵션·미지원 대상 또는 계산 불가능한 보상 룰은 보수적으로 제외한다")
    void rejectsUnsupportedConditions() {
        List<MerchantCardBenefitRuleRow> rows = List.of(
                row("schedule", "include", "all_merchants", null, null, BigDecimal.ZERO,
                        "grant", "discount", "percent", BigDecimal.ONE,
                        null, null, true, false),
                row("option", "include", "all_merchants", null, null, BigDecimal.ZERO,
                        "grant", "discount", "percent", BigDecimal.ONE,
                        null, null, false, true),
                row("target", "include", "channel", null, null, BigDecimal.ZERO,
                        "grant", "discount", "percent", BigDecimal.ONE,
                        null, null, false, false),
                row("effect", "include", "all_merchants", null, null, BigDecimal.ZERO,
                        "bonus", "discount", "percent", BigDecimal.ONE,
                        null, null, false, false),
                row("type", "include", "all_merchants", null, null, BigDecimal.ZERO,
                        "grant", "voucher", "percent", BigDecimal.ONE,
                        null, null, false, false),
                row("unit", "include", "all_merchants", null, null, BigDecimal.ZERO,
                        "grant", "discount", "count", BigDecimal.ONE,
                        null, null, false, false),
                row("value", "include", "all_merchants", null, null, BigDecimal.ZERO,
                        "grant", "discount", "percent", null,
                        null, null, false, false));

        assertEquals(0, evaluate(rows, null, List.of(), null).size());
    }

    private List<?> evaluate(List<MerchantCardBenefitRuleRow> rows, String merchantId,
                             List<String> lineageIds, String confidence) {
        BigDecimal placeConfidence = confidence == null ? null : new BigDecimal(confidence);
        return evaluator.evaluate(rows, merchantId, lineageIds, placeConfidence, USAGE_DATE);
    }

    private MerchantCardBenefitRuleRow row(
            String ruleId, String matchMode, String targetType, String targetCategoryId,
            String targetMerchantId, BigDecimal minimumConfidence, String ruleEffect,
            String rewardType, String rewardUnit, BigDecimal rewardValue,
            LocalDate validFrom, LocalDate validTo, boolean hasSchedule,
            boolean hasOptionRequirement) {
        return new MerchantCardBenefitRuleRow(
                "merchant-1", "이마트", "MART", "마트", "card-1", "카드",
                "카드사", null, "혜택", rewardType, rewardUnit, rewardValue,
                null, null, null, BigDecimal.ZERO, BigDecimal.ONE, null, BigDecimal.ZERO,
                ruleId, ruleEffect, validFrom, validTo, matchMode, targetType,
                targetCategoryId, targetMerchantId, minimumConfidence,
                hasSchedule, hasOptionRequirement);
    }
}
