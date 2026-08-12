package com.moca.mocabe.domain.merchant.recommendation;

import com.moca.mocabe.domain.merchant.model.MerchantCardBenefitCandidate;
import com.moca.mocabe.domain.merchant.model.MerchantCardBenefitRuleRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 조회된 혜택 룰이 현재 가맹점 또는 장소 카테고리에 적용 가능한지 판정한다. */
public class CardBenefitEligibilityEvaluator {
    private static final Set<String> SUPPORTED_REWARD_TYPES =
            Set.of("discount", "cashback", "points", "rebate");
    private static final Set<String> SUPPORTED_REWARD_UNITS =
            Set.of("percent", "KRW", "point", "mile");
    private static final Set<String> SUPPORTED_TARGET_TYPES =
            Set.of("all_merchants", "merchant_category", "merchant");

    public List<MerchantCardBenefitCandidate> evaluate(
            List<MerchantCardBenefitRuleRow> rows, String merchantId,
            List<String> categoryLineageIds, BigDecimal placeConfidence, LocalDate usageDate) {
        Map<String, List<MerchantCardBenefitRuleRow>> rowsByRule = new LinkedHashMap<>();
        for (MerchantCardBenefitRuleRow row : rows) {
            rowsByRule.computeIfAbsent(row.ruleId(), ignored -> new java.util.ArrayList<>()).add(row);
        }
        return rowsByRule.values().stream()
                .filter(ruleRows -> isEligible(
                        ruleRows, merchantId, categoryLineageIds, placeConfidence, usageDate))
                .map(ruleRows -> ruleRows.get(0).toCandidate())
                .toList();
    }

    private boolean isEligible(List<MerchantCardBenefitRuleRow> rows, String merchantId,
                               List<String> categoryLineageIds, BigDecimal placeConfidence,
                               LocalDate usageDate) {
        MerchantCardBenefitRuleRow rule = rows.get(0);
        if (!isSupportedRule(rule, usageDate)) {
            return false;
        }
        boolean hasUnsupportedTarget = rows.stream()
                .anyMatch(row -> !SUPPORTED_TARGET_TYPES.contains(row.targetType()));
        boolean included = rows.stream()
                .filter(row -> "include".equals(row.matchMode()))
                .anyMatch(row -> targetMatches(
                        row, merchantId, categoryLineageIds, placeConfidence, true));
        boolean excluded = rows.stream()
                .filter(row -> "exclude".equals(row.matchMode()))
                .anyMatch(row -> targetMatches(
                        row, merchantId, categoryLineageIds, placeConfidence, false));
        return included && !excluded && !hasUnsupportedTarget;
    }

    private boolean isSupportedRule(MerchantCardBenefitRuleRow rule, LocalDate usageDate) {
        return "grant".equals(rule.ruleEffect())
                && rule.rewardValue() != null
                && SUPPORTED_REWARD_TYPES.contains(rule.rewardType())
                && SUPPORTED_REWARD_UNITS.contains(rule.rewardUnit())
                && (rule.validFrom() == null || !usageDate.isBefore(rule.validFrom()))
                && (rule.validTo() == null || !usageDate.isAfter(rule.validTo()))
                && !rule.hasSchedule()
                && !rule.hasOptionRequirement();
    }

    private boolean targetMatches(MerchantCardBenefitRuleRow row, String merchantId,
                                  List<String> categoryLineageIds, BigDecimal placeConfidence,
                                  boolean applyConfidence) {
        return switch (row.targetType()) {
            case "all_merchants" -> true;
            case "merchant" -> merchantId != null && merchantId.equals(row.targetMerchantId());
            case "merchant_category" -> categoryLineageIds.contains(row.targetMerchantCategoryId())
                    && (!applyConfidence || placeConfidence == null
                    || placeConfidence.compareTo(row.minimumPlaceConfidence()) >= 0);
            default -> false;
        };
    }
}
