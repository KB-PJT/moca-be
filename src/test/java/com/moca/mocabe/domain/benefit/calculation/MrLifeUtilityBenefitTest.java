package com.moca.mocabe.domain.benefit.calculation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moca.mocabe.domain.benefit.model.BenefitCalculationResult;
import com.moca.mocabe.domain.benefit.type.BenefitRejectionReason;

@DisplayName("benefit_title=Mr.Life 공과금 서비스")
class MrLifeUtilityBenefitTest {

    private final MrLifeBenefitTestFixture fixture = new MrLifeBenefitTestFixture();

    @Test
    @DisplayName("10% 할인, 1회 5만원 인정, 30만원 실적 구간 월 3천원 한도를 적용한다")
    void calculatesUtilityDiscount() {
        BenefitCalculationResult result = fixture.calculator.calculate(
                fixture.utilityRule(fixture.value("3000"), "300000", "0"),
                fixture.context("60000", "0", "350000", "2026-07-27T10:00:00",
                        MrLifeBenefitTestFixture.TELECOM_UTILITY));

        fixture.assertApplied(result, "5000", "3000", "0");
    }

    @Test
    @DisplayName("5만원 초과 결제도 1회 5만원까지만 혜택 기준금액으로 인정한다")
    void capsUtilityTransactionAmount() {
        fixture.assertApplied(fixture.calculator.calculate(
                fixture.utilityRule(fixture.value("10000"), "300000", "0"),
                fixture.context("50000", "0", "300000", "2026-07-27T10:00:00",
                        MrLifeBenefitTestFixture.TELECOM_UTILITY)), "5000", "5000", "5000");
        fixture.assertApplied(fixture.calculator.calculate(
                fixture.utilityRule(fixture.value("10000"), "300000", "0"),
                fixture.context("50001", "0", "300000", "2026-07-27T10:00:00",
                        MrLifeBenefitTestFixture.TELECOM_UTILITY)), "5000", "5000", "5000");
    }

    @Test
    @DisplayName("전월 실적 30만원 미만이면 공과금 혜택을 적용하지 않는다")
    void rejectsWhenPreviousMonthSpendIsNotMet() {
        BenefitCalculationResult result = fixture.calculator.calculate(
                fixture.utilityRule(fixture.value("3000"), "300000", "0"),
                fixture.context("50000", "0", "299999", "2026-07-27T10:00:00",
                        MrLifeBenefitTestFixture.TELECOM_UTILITY));

        fixture.assertRejected(result, BenefitRejectionReason.PERFORMANCE_NOT_MET);
    }

    @Test
    @DisplayName("월 한도를 이미 모두 사용했으면 공과금 혜택을 적용하지 않는다")
    void rejectsWhenMonthlyLimitIsExhausted() {
        BenefitCalculationResult result = fixture.calculator.calculate(
                fixture.utilityRule(fixture.value("3000"), "300000", "3000"),
                fixture.context("50000", "0", "350000", "2026-07-27T10:00:00",
                        MrLifeBenefitTestFixture.TELECOM_UTILITY));

        fixture.assertRejected(result, BenefitRejectionReason.MONTHLY_LIMIT_EXHAUSTED);
    }
}
