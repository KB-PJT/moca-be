package com.moca.mocabe.domain.benefit.calculation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moca.mocabe.domain.benefit.model.BenefitCalculationContext;
import com.moca.mocabe.domain.benefit.model.BenefitCalculationResult;
import com.moca.mocabe.domain.benefit.model.BenefitRule;

@DisplayName("benefit_title=Mr.Life 월 통합 한도 정책")
class MrLifeMonthlyLimitPolicyTest {

    private final MrLifeBenefitTestFixture fixture = new MrLifeBenefitTestFixture();

    @Test
    @DisplayName("전월 실적 경계값에 따라 공과금, TIME, 주말 통합 한도를 결정한다")
    void resolvesPerformanceTierBoundaries() {
        assertLimits("299999", "0", "0", "0");
        assertLimits("300000", "3000", "10000", "3000");
        assertLimits("499999", "3000", "10000", "3000");
        assertLimits("500000", "7000", "20000", "7000");
        assertLimits("999999", "7000", "20000", "7000");
        assertLimits("1000000", "10000", "30000", "10000");
    }

    @Test
    @DisplayName("신규 회원 유예 기간에는 공과금, TIME, 주말 서비스 모두 최저 구간 한도를 부여한다")
    void appliesGracePeriodMinimumLimit() {
        BenefitCalculationContext utilityContext = fixture.context("50000", "0", "0", "2026-07-27T10:00:00",
                MrLifeBenefitTestFixture.TELECOM_UTILITY, true, 0, 0, true, true);
        BenefitCalculationContext timeContext = fixture.context("15000", "0", "0", "2026-07-27T10:00:00",
                MrLifeBenefitTestFixture.CONVENIENCE_STORE, true, 0, 0, true, true);
        BenefitCalculationContext weekendContext = fixture.context("70000", "0", "0", "2026-08-01T14:00:00",
                MrLifeBenefitTestFixture.OFFLINE_SHOPPING, true, 0, 0, true, true);

        BenefitCalculationResult utilityResult = fixture.calculator.calculate(
                fixture.utilityRule(fixture.limitPolicy.utilityLimit(utilityContext), "0", "0"), utilityContext);
        BenefitCalculationResult timeResult = fixture.calculator.calculate(
                fixture.timeRule("mr-life-convenience", MrLifeBenefitTestFixture.CONVENIENCE_STORE, "10000",
                        fixture.limitPolicy.timeLimit(timeContext).toPlainString(), 1, 5), timeContext);
        BenefitCalculationResult weekendResult = fixture.calculator.calculate(
                fixture.weekendMartRule(fixture.limitPolicy.weekendLimit(weekendContext).toPlainString(), "0", 0, 0),
                weekendContext);

        fixture.assertApplied(utilityResult, "5000", "3000", "0");
        fixture.assertApplied(timeResult, "1000", "1000", "9000");
        fixture.assertApplied(weekendResult, "5000", "3000", "0");
    }

    @Test
    @DisplayName("할인을 받은 거래금액과 집계기가 보정한 특수 거래금액도 전월 실적 입력값으로 사용한다")
    void usesAggregatedPreviousMonthSpendInput() {
        BenefitRule utilityRule = fixture.utilityRule(fixture.value("3000"), "300000", "0");
        BenefitCalculationResult utilityResult = fixture.calculator.calculate(utilityRule,
                fixture.context("50000", "0", "300000", "2026-07-27T10:00:00",
                        MrLifeBenefitTestFixture.TELECOM_UTILITY));

        BenefitRule taxiRule = fixture.timeRule("mr-life-taxi", MrLifeBenefitTestFixture.TAXI_MOBILITY,
                "10000", "10000", 1, 10);
        BenefitCalculationResult taxiResult = fixture.calculator.calculate(taxiRule,
                fixture.context("10000", "0", "300000", "2026-07-27T21:00:00",
                        MrLifeBenefitTestFixture.TAXI_MOBILITY));

        fixture.assertApplied(utilityResult, "5000", "3000", "0");
        fixture.assertApplied(taxiResult, "1000", "1000", "9000");
    }

    private void assertLimits(String previousMonthSpend, String utilityLimit, String timeLimit, String weekendLimit) {
        BenefitCalculationContext context = fixture.context("0", "0", previousMonthSpend, "2026-07-27T10:00:00",
                MrLifeBenefitTestFixture.TELECOM_UTILITY);

        fixture.assertBigDecimalEquals(utilityLimit, fixture.limitPolicy.utilityLimit(context));
        fixture.assertBigDecimalEquals(timeLimit, fixture.limitPolicy.timeLimit(context));
        fixture.assertBigDecimalEquals(weekendLimit, fixture.limitPolicy.weekendLimit(context));
    }
}
