package com.moca.mocabe.domain.benefit.calculation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moca.mocabe.domain.benefit.model.BenefitCalculationResult;
import com.moca.mocabe.domain.benefit.type.BenefitRejectionReason;

@DisplayName("benefit_title=Mr.Life 인테이크몰 서비스")
class MrLifeIntakeMallBenefitTest {

    private final MrLifeBenefitTestFixture fixture = new MrLifeBenefitTestFixture();

    @Test
    @DisplayName("20% 할인은 전월 실적과 관계없이 적용한다")
    void appliesIntakeMallDiscountWithoutPerformanceRequirement() {
        BenefitCalculationResult result = fixture.calculator.calculate(fixture.intakeRule("8000", 0),
                fixture.context("40000", "0", "0", "2026-07-27T10:00:00",
                        MrLifeBenefitTestFixture.ONLINE_SHOPPING));

        fixture.assertApplied(result, "8000", "8000", "0");
    }

    @Test
    @DisplayName("전용 결제창 직접 결제만 적용하고 간편결제는 제외한다")
    void filtersPaymentChannel() {
        fixture.assertApplied(fixture.calculator.calculate(fixture.intakeRule("8000", 0),
                fixture.context("40000", "0", "0", "2026-07-27T10:00:00",
                        MrLifeBenefitTestFixture.ONLINE_SHOPPING)), "8000", "8000", "0");

        BenefitCalculationResult result = fixture.calculator.calculate(fixture.intakeRule("8000", 0),
                fixture.context("40000", "0", "0", "2026-07-27T10:00:00",
                        MrLifeBenefitTestFixture.ONLINE_SHOPPING, false, 0, 0, true, false));

        fixture.assertRejected(result, BenefitRejectionReason.PAYMENT_CHANNEL_NOT_ELIGIBLE);
    }

    @Test
    @DisplayName("월 4회까지 적용하고 5회차부터는 적용하지 않는다")
    void validatesMonthlyFrequencyLimit() {
        assertTrue(fixture.calculator.calculate(fixture.intakeRule("8000", 4),
                fixture.context("40000", "0", "0", "2026-07-27T10:00:00",
                        MrLifeBenefitTestFixture.ONLINE_SHOPPING, false, 0, 3, true, true)).applicable());
        fixture.assertRejected(fixture.calculator.calculate(fixture.intakeRule("8000", 4),
                fixture.context("40000", "0", "0", "2026-07-27T10:00:00",
                        MrLifeBenefitTestFixture.ONLINE_SHOPPING, false, 0, 4, true, true)),
                BenefitRejectionReason.FREQUENCY_LIMIT_EXHAUSTED);
    }
}
