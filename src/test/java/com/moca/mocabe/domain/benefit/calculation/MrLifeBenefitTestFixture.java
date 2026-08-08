package com.moca.mocabe.domain.benefit.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import com.moca.mocabe.domain.benefit.model.BenefitCalculationContext;
import com.moca.mocabe.domain.benefit.model.BenefitCalculationResult;
import com.moca.mocabe.domain.benefit.model.BenefitRule;
import com.moca.mocabe.domain.benefit.type.BenefitBasis;
import com.moca.mocabe.domain.benefit.type.BenefitPromotionCondition;
import com.moca.mocabe.domain.benefit.type.BenefitRejectionReason;
import com.moca.mocabe.domain.benefit.type.BenefitType;
import com.moca.mocabe.domain.benefit.type.RewardUnit;

/**
 * 신한카드 Mr.Life benefit_title별 테스트에서 공통으로 사용하는 룰과 검증 도우미다.
 */
final class MrLifeBenefitTestFixture {

    static final String CONVENIENCE_STORE = "CONVENIENCE_STORE";
    static final String FUEL_CAR = "FUEL_CAR";
    static final String FOOD_DINING = "FOOD_DINING";
    static final String LAUNDRY = "LAUNDRY";
    static final String MEDICAL_BEAUTY = "MEDICAL_BEAUTY";
    static final String OFFLINE_SHOPPING = "OFFLINE_SHOPPING";
    static final String ONLINE_SHOPPING = "ONLINE_SHOPPING";
    static final String TAXI_MOBILITY = "TAXI_MOBILITY";
    static final String TELECOM_UTILITY = "TELECOM_UTILITY";

    final BenefitCalculator calculator = new PromotionBenefitCalculator();
    final MrLifeMonthlyLimitPolicy limitPolicy = new MrLifeMonthlyLimitPolicy();

    BenefitRule utilityRule(BigDecimal monthlyLimit, String requiredPreviousMonthSpend, String usedMonthlyValue) {
        return rateRule("mr-life-utility", TELECOM_UTILITY, "50000", requiredPreviousMonthSpend,
                monthlyLimit.toPlainString(), usedMonthlyValue, BenefitPromotionCondition.NONE, 0, 0, false, false);
    }

    BenefitRule timeRule(String ruleId, String category, String maximumBenefitBaseAmount,
            String monthlyLimitValue, int dailyUsageLimit, int monthlyUsageLimit) {
        return timeRuleWithUsedMonthly(ruleId, category, maximumBenefitBaseAmount, monthlyLimitValue, "0",
                dailyUsageLimit, monthlyUsageLimit);
    }

    BenefitRule timeRuleWithUsedMonthly(String ruleId, String category, String maximumBenefitBaseAmount,
            String monthlyLimitValue, String usedMonthlyValue, int dailyUsageLimit, int monthlyUsageLimit) {
        return rateRule(ruleId, category, maximumBenefitBaseAmount, "300000", monthlyLimitValue, usedMonthlyValue,
                promotionCondition(ruleId), dailyUsageLimit, monthlyUsageLimit, false, false);
    }

    BenefitRule eligibleMerchantRule(String ruleId, String category) {
        return rateRule(ruleId, category, "10000", "300000", "10000", "0", BenefitPromotionCondition.NONE,
                0, 0, true, false);
    }

    BenefitRule weekendMartRule(String monthlyLimitValue, String usedMonthlyValue, int dailyUsageLimit,
            int monthlyUsageLimit) {
        return rateRule("mr-life-weekend-mart", OFFLINE_SHOPPING, "50000", "300000", monthlyLimitValue,
                usedMonthlyValue, BenefitPromotionCondition.WEEKEND, dailyUsageLimit, monthlyUsageLimit, true, false);
    }

    BenefitRule fuelRule(String monthlyLimitValue, String usedMonthlyValue) {
        return new BenefitRule("mr-life-weekend-fuel", BenefitType.DISCOUNT, BenefitBasis.PER_USAGE_UNIT,
                RewardUnit.KRW, zero(), value("60"), zero(), value("100000"), zero(), value("300000"),
                value(monthlyLimitValue), value(usedMonthlyValue), BenefitPromotionCondition.WEEKEND,
                Set.of(FUEL_CAR), 0, 0, true, false);
    }

    BenefitRule intakeRule(String monthlyLimitValue, int monthlyUsageLimit) {
        return new BenefitRule("mr-life-intake-mall", BenefitType.DISCOUNT, BenefitBasis.RATE, RewardUnit.KRW,
                value("0.20"), zero(), zero(), zero(), zero(), zero(), value(monthlyLimitValue), zero(),
                BenefitPromotionCondition.NONE, Set.of(ONLINE_SHOPPING), 0, monthlyUsageLimit, false, true);
    }

    BenefitRule noLimitNightRule() {
        return new BenefitRule("mr-life-night-no-limit", BenefitType.DISCOUNT, BenefitBasis.RATE, RewardUnit.KRW,
                value("0.10"), zero(), zero(), value("10000"), zero(), value("300000"), zero(), zero(),
                BenefitPromotionCondition.NIGHT_TIME, Set.of(TAXI_MOBILITY));
    }

    BenefitCalculationContext context(String paymentAmount, String usageQuantity, String previousMonthSpend,
            String approvedAt, String mocaCategory) {
        return context(paymentAmount, usageQuantity, previousMonthSpend, approvedAt, mocaCategory,
                false, 0, 0, true, true);
    }

    BenefitCalculationContext context(String paymentAmount, String usageQuantity, String previousMonthSpend,
            String approvedAt, String mocaCategory, boolean newMemberGracePeriod, int usedDailyCount,
            int usedMonthlyCount, boolean merchantEligible, boolean paymentChannelEligible) {
        return new BenefitCalculationContext(value(paymentAmount), value(usageQuantity), value(previousMonthSpend),
                LocalDateTime.parse(approvedAt), mocaCategory, newMemberGracePeriod, usedDailyCount,
                usedMonthlyCount, merchantEligible, paymentChannelEligible);
    }

    BenefitCalculationContext contextWithoutApprovedAt(String paymentAmount, String usageQuantity,
            String previousMonthSpend, String mocaCategory) {
        return new BenefitCalculationContext(value(paymentAmount), value(usageQuantity), value(previousMonthSpend),
                null, mocaCategory);
    }

    void assertApplied(BenefitCalculationResult result, String rawRewardValue, String appliedRewardValue,
            String remainingLimitValue) {
        assertTrue(result.applicable());
        assertBigDecimalEquals(rawRewardValue, result.rawRewardValue());
        assertBigDecimalEquals(appliedRewardValue, result.appliedRewardValue());
        assertBigDecimalEquals(remainingLimitValue, result.remainingLimitValue());
        assertEquals(BenefitRejectionReason.NONE, result.rejectionReason());
    }

    void assertRejected(BenefitCalculationResult result, BenefitRejectionReason rejectionReason) {
        assertFalse(result.applicable());
        assertEquals(rejectionReason, result.rejectionReason());
    }

    void assertBigDecimalEquals(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    BigDecimal value(String value) {
        return new BigDecimal(value);
    }

    private BenefitRule rateRule(String ruleId, String category, String maximumBenefitBaseAmount,
            String requiredPreviousMonthSpend, String monthlyLimitValue, String usedMonthlyValue,
            BenefitPromotionCondition condition, int dailyUsageLimit, int monthlyUsageLimit,
            boolean merchantEligibilityRequired, boolean paymentChannelEligibilityRequired) {
        return new BenefitRule(ruleId, BenefitType.DISCOUNT, BenefitBasis.RATE, RewardUnit.KRW, value("0.10"),
                zero(), zero(), value(maximumBenefitBaseAmount), zero(), value(requiredPreviousMonthSpend),
                value(monthlyLimitValue), value(usedMonthlyValue), condition, Set.of(category), dailyUsageLimit,
                monthlyUsageLimit, merchantEligibilityRequired, paymentChannelEligibilityRequired);
    }

    private BenefitPromotionCondition promotionCondition(String ruleId) {
        if (ruleId.contains("online-shopping") || ruleId.contains("taxi") || ruleId.contains("food")) {
            return BenefitPromotionCondition.NIGHT_TIME;
        }
        if (ruleId.contains("weekend")) {
            return BenefitPromotionCondition.WEEKEND;
        }
        return BenefitPromotionCondition.NONE;
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO;
    }
}
