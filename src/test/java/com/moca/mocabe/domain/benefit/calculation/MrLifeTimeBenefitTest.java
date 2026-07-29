package com.moca.mocabe.domain.benefit.calculation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moca.mocabe.domain.benefit.model.BenefitCalculationContext;
import com.moca.mocabe.domain.benefit.model.BenefitCalculationResult;
import com.moca.mocabe.domain.benefit.model.BenefitRule;
import com.moca.mocabe.domain.benefit.type.BenefitRejectionReason;

@DisplayName("benefit_title=Mr.Life TIME 할인 서비스")
class MrLifeTimeBenefitTest {

    private final MrLifeBenefitTestFixture fixture = new MrLifeBenefitTestFixture();

    @Test
    @DisplayName("편의점은 10% 할인, 1회 1만원까지만 인정하고 원 미만을 절사한다")
    void calculatesConvenienceStoreDiscount() {
        BenefitRule rule = fixture.timeRule("mr-life-convenience", MrLifeBenefitTestFixture.CONVENIENCE_STORE,
                "10000", "10000", 1, 5);

        fixture.assertApplied(fixture.calculator.calculate(rule,
                fixture.context("10000", "0", "300000", "2026-07-27T10:00:00",
                        MrLifeBenefitTestFixture.CONVENIENCE_STORE)), "1000", "1000", "9000");
        fixture.assertApplied(fixture.calculator.calculate(rule,
                fixture.context("15000", "0", "300000", "2026-07-27T10:00:00",
                        MrLifeBenefitTestFixture.CONVENIENCE_STORE)), "1000", "1000", "9000");
        fixture.assertApplied(fixture.calculator.calculate(rule,
                fixture.context("9990", "0", "300000", "2026-07-27T10:00:00",
                        MrLifeBenefitTestFixture.CONVENIENCE_STORE)), "999", "999", "9001");
    }

    @Test
    @DisplayName("병원/약국과 세탁소 생활 혜택은 10% 할인, 1회 1만원까지 적용한다")
    void calculatesMedicalAndLaundryDiscount() {
        BenefitCalculationResult medical = fixture.calculator.calculate(
                fixture.timeRule("mr-life-medical", MrLifeBenefitTestFixture.MEDICAL_BEAUTY,
                        "10000", "10000", 1, 5),
                fixture.context("8000", "0", "300000", "2026-07-27T10:00:00",
                        MrLifeBenefitTestFixture.MEDICAL_BEAUTY));
        BenefitCalculationResult laundry = fixture.calculator.calculate(
                fixture.timeRule("mr-life-laundry", MrLifeBenefitTestFixture.LAUNDRY, "10000", "10000", 1, 5),
                fixture.context("12000", "0", "300000", "2026-07-27T10:00:00",
                        MrLifeBenefitTestFixture.LAUNDRY));

        fixture.assertApplied(medical, "800", "800", "9200");
        fixture.assertApplied(laundry, "1000", "1000", "9000");
    }

    @Test
    @DisplayName("Night 온라인쇼핑, 택시, 식음료는 21시부터 09시 전까지 적용한다")
    void calculatesNightDiscounts() {
        BenefitCalculationResult onlineShopping = fixture.calculator.calculate(
                fixture.timeRule("mr-life-online-shopping", MrLifeBenefitTestFixture.ONLINE_SHOPPING,
                        "10000", "10000", 1, 10),
                fixture.context("9900", "0", "300000", "2026-07-27T22:00:00",
                        MrLifeBenefitTestFixture.ONLINE_SHOPPING));
        BenefitCalculationResult taxi = fixture.calculator.calculate(
                fixture.timeRule("mr-life-taxi", MrLifeBenefitTestFixture.TAXI_MOBILITY, "10000", "10000", 1, 10),
                fixture.context("30000", "0", "300000", "2026-07-27T22:00:00",
                        MrLifeBenefitTestFixture.TAXI_MOBILITY));
        BenefitCalculationResult food = fixture.calculator.calculate(
                fixture.timeRule("mr-life-food", MrLifeBenefitTestFixture.FOOD_DINING, "10000", "10000", 1, 10),
                fixture.context("7000", "0", "300000", "2026-07-27T22:00:00",
                        MrLifeBenefitTestFixture.FOOD_DINING));

        fixture.assertApplied(onlineShopping, "990", "990", "9010");
        fixture.assertApplied(taxi, "1000", "1000", "9000");
        fixture.assertApplied(food, "700", "700", "9300");
    }

    @Test
    @DisplayName("Night 경계값은 21:00:00 포함, 09:00:00 제외로 판단한다")
    void validatesNightTimeBoundaries() {
        BenefitRule rule = fixture.timeRule("mr-life-taxi", MrLifeBenefitTestFixture.TAXI_MOBILITY,
                "10000", "10000", 1, 10);

        assertNightRejected(rule, "2026-07-27T20:59:59");
        fixture.assertApplied(fixture.calculator.calculate(rule,
                fixture.context("10000", "0", "300000", "2026-07-27T21:00:00",
                        MrLifeBenefitTestFixture.TAXI_MOBILITY)), "1000", "1000", "9000");
        fixture.assertApplied(fixture.calculator.calculate(rule,
                fixture.context("10000", "0", "300000", "2026-07-28T08:59:59",
                        MrLifeBenefitTestFixture.TAXI_MOBILITY)), "1000", "1000", "9000");
        assertNightRejected(rule, "2026-07-28T09:00:00");
    }

    @Test
    @DisplayName("승인 시각이 없으면 Night 조건을 확인할 수 없어 적용하지 않는다")
    void rejectsNightBenefitWithoutApprovedAt() {
        BenefitCalculationResult result = fixture.calculator.calculate(fixture.noLimitNightRule(),
                fixture.contextWithoutApprovedAt("10000", "0", "300000",
                        MrLifeBenefitTestFixture.TAXI_MOBILITY));

        fixture.assertRejected(result, BenefitRejectionReason.CONDITION_NOT_MET);
        fixture.assertBigDecimalEquals("0", result.remainingLimitValue());
    }

    @Test
    @DisplayName("일 1회와 월 횟수 제한은 영역별 허용 횟수까지만 적용한다")
    void validatesFrequencyLimits() {
        BenefitRule convenienceRule = fixture.timeRule("mr-life-convenience",
                MrLifeBenefitTestFixture.CONVENIENCE_STORE, "10000", "10000", 1, 5);
        BenefitCalculationContext dailyUsed = fixture.context("5000", "0", "300000",
                "2026-07-27T10:00:00", MrLifeBenefitTestFixture.CONVENIENCE_STORE, false, 1, 0, true, true);

        fixture.assertRejected(fixture.calculator.calculate(convenienceRule, dailyUsed),
                BenefitRejectionReason.FREQUENCY_LIMIT_EXHAUSTED);
        assertTrue(fixture.calculator.calculate(convenienceRule,
                fixture.context("5000", "0", "300000", "2026-07-27T10:00:00",
                        MrLifeBenefitTestFixture.CONVENIENCE_STORE, false, 0, 4, true, true)).applicable());
        assertFrequencyRejected(fixture.timeRule("mr-life-medical", MrLifeBenefitTestFixture.MEDICAL_BEAUTY,
                "10000", "10000", 1, 5), MrLifeBenefitTestFixture.MEDICAL_BEAUTY, 5);
        assertFrequencyRejected(fixture.timeRule("mr-life-online-shopping", MrLifeBenefitTestFixture.ONLINE_SHOPPING,
                "10000", "10000", 1, 10), MrLifeBenefitTestFixture.ONLINE_SHOPPING, 10);
    }

    @Test
    @DisplayName("통합 한도는 전표 매입 순서대로 차감하고 잔액 부족 시 남은 금액만 적용한다")
    void appliesLimitByCaptureOrderAndRemainingLimit() {
        BenefitRule capturedFirst = fixture.timeRule("mr-life-food", MrLifeBenefitTestFixture.FOOD_DINING,
                "10000", "10000", 1, 10);
        BenefitRule capturedSecond = fixture.timeRuleWithUsedMonthly("mr-life-food",
                MrLifeBenefitTestFixture.FOOD_DINING, "10000", "10000", "1000", 1, 10);
        BenefitRule partialRule = fixture.timeRuleWithUsedMonthly("mr-life-convenience",
                MrLifeBenefitTestFixture.CONVENIENCE_STORE, "10000", "10000", "9500", 1, 5);
        BenefitRule exhaustedRule = fixture.timeRuleWithUsedMonthly("mr-life-convenience",
                MrLifeBenefitTestFixture.CONVENIENCE_STORE, "10000", "10000", "10000", 1, 5);

        fixture.assertApplied(fixture.calculator.calculate(capturedFirst,
                fixture.context("10000", "0", "300000", "2026-07-27T21:05:00",
                        MrLifeBenefitTestFixture.FOOD_DINING)), "1000", "1000", "9000");
        fixture.assertApplied(fixture.calculator.calculate(capturedSecond,
                fixture.context("10000", "0", "300000", "2026-07-27T21:00:00",
                        MrLifeBenefitTestFixture.FOOD_DINING)), "1000", "1000", "8000");
        fixture.assertApplied(fixture.calculator.calculate(partialRule,
                fixture.context("10000", "0", "300000", "2026-07-27T10:00:00",
                        MrLifeBenefitTestFixture.CONVENIENCE_STORE)), "1000", "500", "0");
        fixture.assertRejected(fixture.calculator.calculate(exhaustedRule,
                fixture.context("10000", "0", "300000", "2026-07-27T10:00:00",
                        MrLifeBenefitTestFixture.CONVENIENCE_STORE)),
                BenefitRejectionReason.MONTHLY_LIMIT_EXHAUSTED);
    }

    @Test
    @DisplayName("치과와 한의원은 적용하고 동물병원은 제외한다")
    void filtersMedicalMerchantEligibility() {
        assertTrue(fixture.calculator.calculate(
                fixture.eligibleMerchantRule("mr-life-dental", MrLifeBenefitTestFixture.MEDICAL_BEAUTY),
                fixture.context("10000", "0", "300000", "2026-07-27T10:00:00",
                        MrLifeBenefitTestFixture.MEDICAL_BEAUTY)).applicable());
        assertTrue(fixture.calculator.calculate(
                fixture.eligibleMerchantRule("mr-life-oriental-clinic", MrLifeBenefitTestFixture.MEDICAL_BEAUTY),
                fixture.context("10000", "0", "300000", "2026-07-27T10:00:00",
                        MrLifeBenefitTestFixture.MEDICAL_BEAUTY)).applicable());
        fixture.assertRejected(fixture.calculator.calculate(
                fixture.eligibleMerchantRule("mr-life-animal-hospital", MrLifeBenefitTestFixture.MEDICAL_BEAUTY),
                fixture.context("10000", "0", "300000", "2026-07-27T10:00:00",
                        MrLifeBenefitTestFixture.MEDICAL_BEAUTY, false, 0, 0, false, true)),
                BenefitRejectionReason.MERCHANT_NOT_ELIGIBLE);
    }

    private void assertNightRejected(BenefitRule rule, String approvedAt) {
        fixture.assertRejected(fixture.calculator.calculate(rule,
                fixture.context("10000", "0", "300000", approvedAt,
                        MrLifeBenefitTestFixture.TAXI_MOBILITY)), BenefitRejectionReason.CONDITION_NOT_MET);
    }

    private void assertFrequencyRejected(BenefitRule rule, String category, int usedMonthlyCount) {
        String approvedAt = rule.promotionCondition().name().equals("NIGHT_TIME")
                ? "2026-07-27T21:00:00"
                : "2026-07-27T10:00:00";
        fixture.assertRejected(fixture.calculator.calculate(rule,
                fixture.context("5000", "0", "300000", approvedAt, category,
                        false, 0, usedMonthlyCount, true, true)),
                BenefitRejectionReason.FREQUENCY_LIMIT_EXHAUSTED);
    }
}
