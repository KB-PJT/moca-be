package com.moca.mocabe.domain.benefit.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moca.mocabe.domain.benefit.model.BenefitCalculationContext;
import com.moca.mocabe.domain.benefit.model.BenefitCalculationResult;
import com.moca.mocabe.domain.benefit.model.BenefitRule;
import com.moca.mocabe.domain.benefit.type.BenefitBasis;
import com.moca.mocabe.domain.benefit.type.BenefitPromotionCondition;
import com.moca.mocabe.domain.benefit.type.BenefitRejectionReason;
import com.moca.mocabe.domain.benefit.type.BenefitType;
import com.moca.mocabe.domain.benefit.type.RewardUnit;

@DisplayName("카드 혜택 계산기")
class BenefitCalculatorTest {

    private final BenefitCalculator calculator = new BasicBenefitCalculator();

    @Test
    @DisplayName("정률 할인은 월 한도를 반영해 실제 적용 금액을 계산한다")
    void calculatesRateDiscountWithMonthlyLimit() {
        BenefitRule rule = rule("rule-1", BenefitType.DISCOUNT, BenefitBasis.RATE, RewardUnit.KRW,
                "0.10", "0", "0", "0", "10000", "300000", "5000", "1000", "CAFE");
        BenefitCalculationContext context = context("50000", "0", "350000", "CAFE");

        BenefitCalculationResult result = calculator.calculate(rule, context);

        assertTrue(result.applicable());
        assertEquals("rule-1", result.ruleId());
        assertEquals(BenefitType.DISCOUNT, result.benefitType());
        assertEquals(RewardUnit.KRW, result.rewardUnit());
        assertBigDecimalEquals("5000", result.rawRewardValue());
        assertBigDecimalEquals("4000", result.appliedRewardValue());
        assertBigDecimalEquals("0", result.remainingLimitValue());
        assertEquals(BenefitRejectionReason.NONE, result.rejectionReason());
    }

    @Test
    @DisplayName("정액 할인은 결제금액을 초과해서 적용되지 않는다")
    void calculatesFixedDiscountWithoutExceedingPaymentAmount() {
        BenefitRule rule = rule("rule-2", BenefitType.DISCOUNT, BenefitBasis.FIXED, RewardUnit.KRW,
                "0", "5000", "0", "0", "0", "0", "0", "0");
        BenefitCalculationContext context = context("3000", "0", "0", "FOOD_DINING");

        BenefitCalculationResult result = calculator.calculate(rule, context);

        assertTrue(result.applicable());
        assertBigDecimalEquals("3000", result.rawRewardValue());
        assertBigDecimalEquals("3000", result.appliedRewardValue());
        assertBigDecimalEquals("0", result.remainingLimitValue());
    }

    @Test
    @DisplayName("정액 캐시백은 할인과 달리 결제금액으로 상한 처리하지 않는다")
    void calculatesFixedCashbackWithoutPaymentAmountCap() {
        BenefitRule rule = rule("rule-3", BenefitType.CASHBACK, BenefitBasis.FIXED, RewardUnit.KRW,
                "0", "5000", "0", "0", "0", "0", "0", "0");
        BenefitCalculationContext context = context("3000", "0", "0", "ONLINE_SHOPPING");

        BenefitCalculationResult result = calculator.calculate(rule, context);

        assertTrue(result.applicable());
        assertBigDecimalEquals("5000", result.rawRewardValue());
        assertBigDecimalEquals("5000", result.appliedRewardValue());
    }

    @Test
    @DisplayName("포인트는 결제 단위 금액을 기준으로 적립한다")
    void calculatesPointRewardBySpendUnit() {
        BenefitRule rule = rule("rule-4", BenefitType.POINT, BenefitBasis.PER_SPEND_UNIT, RewardUnit.POINT,
                "0", "1", "1000", "0", "0", "0", "0", "0", "ALL_MERCHANTS");
        BenefitCalculationContext context = context("12900", "0", "0", "ALL_MERCHANTS");

        BenefitCalculationResult result = calculator.calculate(rule, context);

        assertTrue(result.applicable());
        assertBigDecimalEquals("12", result.rawRewardValue());
        assertBigDecimalEquals("12", result.appliedRewardValue());
    }

    @Test
    @DisplayName("마일리지는 결제 단위로 계산한 뒤 월 한도를 반영한다")
    void calculatesMileageRewardBySpendUnitWithMonthlyLimit() {
        BenefitRule rule = rule("rule-5", BenefitType.MILEAGE, BenefitBasis.PER_SPEND_UNIT, RewardUnit.MILE,
                "0", "2", "1000", "0", "0", "0", "20", "18", "OVERSEAS");
        BenefitCalculationContext context = context("5000", "0", "0", "OVERSEAS");

        BenefitCalculationResult result = calculator.calculate(rule, context);

        assertTrue(result.applicable());
        assertBigDecimalEquals("10", result.rawRewardValue());
        assertBigDecimalEquals("2", result.appliedRewardValue());
        assertBigDecimalEquals("0", result.remainingLimitValue());
    }

    @Test
    @DisplayName("결제 단위가 잘못된 룰은 혜택값을 0으로 계산한다")
    void returnsZeroForInvalidSpendUnit() {
        BenefitRule rule = rule("rule-6", BenefitType.POINT, BenefitBasis.PER_SPEND_UNIT, RewardUnit.POINT,
                "0", "1", "0", "0", "0", "0", "0", "0", "CAFE");
        BenefitCalculationContext context = context("5000", "0", "0", "CAFE");

        BenefitCalculationResult result = calculator.calculate(rule, context);

        assertTrue(result.applicable());
        assertBigDecimalEquals("0", result.rawRewardValue());
        assertBigDecimalEquals("0", result.appliedRewardValue());
    }

    @Test
    @DisplayName("비율 계산 결과의 소수점 이하는 절사한다")
    void floorsRateRewardDecimals() {
        BenefitRule rule = rule("rule-11", BenefitType.DISCOUNT, BenefitBasis.RATE, RewardUnit.KRW,
                "0.033", "0", "0", "0", "0", "0", "0", "0", "CAFE");
        BenefitCalculationContext context = context("9999", "0", "0", "CAFE");

        BenefitCalculationResult result = calculator.calculate(rule, context);

        assertTrue(result.applicable());
        assertBigDecimalEquals("329", result.rawRewardValue());
    }

    @Test
    @DisplayName("정률 할인은 거래당 혜택 기준금액 상한을 먼저 반영한다")
    void calculatesRateDiscountWithMaximumBenefitBaseAmount() {
        BenefitRule rule = rule("rule-12", BenefitType.DISCOUNT, BenefitBasis.RATE, RewardUnit.KRW,
                "0.10", "0", "0", "10000", "0", "0", "0", "0", "CONVENIENCE_STORE");
        BenefitCalculationContext context = context("30000", "0", "0", "CONVENIENCE_STORE");

        BenefitCalculationResult result = calculator.calculate(rule, context);

        assertTrue(result.applicable());
        assertBigDecimalEquals("1000", result.rawRewardValue());
    }

    @Test
    @DisplayName("사용량 기반 혜택은 결제금액이 아닌 사용량 단위로 계산한다")
    void calculatesRewardByUsageUnit() {
        BenefitRule rule = rule("rule-13", BenefitType.DISCOUNT, BenefitBasis.PER_USAGE_UNIT, RewardUnit.KRW,
                "0", "60", "0", "0", "0", "0", "0", "0", "FUEL_CAR");
        BenefitCalculationContext context = context("90000", "42.7", "0", "FUEL_CAR");

        BenefitCalculationResult result = calculator.calculate(rule, context);

        assertTrue(result.applicable());
        assertBigDecimalEquals("2562", result.rawRewardValue());
    }

    @Test
    @DisplayName("카테고리가 맞지 않으면 혜택을 적용하지 않는다")
    void rejectsWhenCategoryIsNotMatched() {
        BenefitRule rule = rule("rule-7", BenefitType.DISCOUNT, BenefitBasis.RATE, RewardUnit.KRW,
                "0.10", "0", "0", "0", "0", "0", "5000", "1000", "CAFE");
        BenefitCalculationContext context = context("10000", "0", "0", "MOVIE_CULTURE");

        BenefitCalculationResult result = calculator.calculate(rule, context);

        assertFalse(result.applicable());
        assertBigDecimalEquals("4000", result.remainingLimitValue());
        assertEquals(BenefitRejectionReason.CATEGORY_NOT_MATCHED, result.rejectionReason());
    }

    @Test
    @DisplayName("최소 결제금액을 충족하지 못하면 혜택을 적용하지 않는다")
    void rejectsWhenMinimumPaymentIsNotMet() {
        BenefitRule rule = rule("rule-8", BenefitType.DISCOUNT, BenefitBasis.RATE, RewardUnit.KRW,
                "0.10", "0", "0", "0", "10000", "0", "0", "0", "CAFE");
        BenefitCalculationContext context = context("9900", "0", "0", "CAFE");

        BenefitCalculationResult result = calculator.calculate(rule, context);

        assertFalse(result.applicable());
        assertEquals(BenefitRejectionReason.MIN_PAYMENT_NOT_MET, result.rejectionReason());
    }

    @Test
    @DisplayName("전월실적을 충족하지 못하면 혜택을 적용하지 않는다")
    void rejectsWhenPreviousMonthSpendIsNotMet() {
        BenefitRule rule = rule("rule-9", BenefitType.DISCOUNT, BenefitBasis.RATE, RewardUnit.KRW,
                "0.10", "0", "0", "0", "0", "300000", "0", "0", "CAFE");
        BenefitCalculationContext context = context("10000", "0", "299999", "CAFE");

        BenefitCalculationResult result = calculator.calculate(rule, context);

        assertFalse(result.applicable());
        assertEquals(BenefitRejectionReason.PERFORMANCE_NOT_MET, result.rejectionReason());
    }

    @Test
    @DisplayName("월 한도를 모두 사용했으면 혜택을 적용하지 않는다")
    void rejectsWhenMonthlyLimitIsExhausted() {
        BenefitRule rule = rule("rule-10", BenefitType.DISCOUNT, BenefitBasis.RATE, RewardUnit.KRW,
                "0.10", "0", "0", "0", "0", "0", "5000", "5000", "CAFE");
        BenefitCalculationContext context = context("10000", "0", "0", "CAFE");

        BenefitCalculationResult result = calculator.calculate(rule, context);

        assertFalse(result.applicable());
        assertBigDecimalEquals("1000", result.rawRewardValue());
        assertBigDecimalEquals("0", result.appliedRewardValue());
        assertBigDecimalEquals("0", result.remainingLimitValue());
        assertEquals(BenefitRejectionReason.MONTHLY_LIMIT_EXHAUSTED, result.rejectionReason());
    }

    private BenefitRule rule(String ruleId, BenefitType benefitType, BenefitBasis benefitBasis, RewardUnit rewardUnit,
            String rewardRate, String rewardValue, String spendUnitAmount, String maximumBenefitBaseAmount,
            String minimumPaymentAmount, String requiredPreviousMonthSpend, String monthlyLimitValue,
            String usedMonthlyValue, String... mocaCategories) {
        return new BenefitRule(ruleId, benefitType, benefitBasis, rewardUnit, value(rewardRate), value(rewardValue),
                value(spendUnitAmount), value(maximumBenefitBaseAmount), value(minimumPaymentAmount),
                value(requiredPreviousMonthSpend), value(monthlyLimitValue), value(usedMonthlyValue),
                BenefitPromotionCondition.NONE, Set.of(mocaCategories));
    }

    private BenefitCalculationContext context(String paymentAmount, String usageQuantity, String previousMonthSpend,
            String mocaCategory) {
        return new BenefitCalculationContext(value(paymentAmount), value(usageQuantity), value(previousMonthSpend),
                null, mocaCategory);
    }

    private BigDecimal value(String value) {
        return new BigDecimal(value);
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO;
    }

    private void assertBigDecimalEquals(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
