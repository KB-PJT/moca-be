package com.moca.mocabe.domain.benefit.calculation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moca.mocabe.domain.benefit.model.BenefitCalculationResult;
import com.moca.mocabe.domain.benefit.model.BenefitRule;
import com.moca.mocabe.domain.benefit.type.BenefitRejectionReason;

@DisplayName("benefit_title=Mr.Life 주말 할인 서비스")
class MrLifeWeekendBenefitTest {

    private final MrLifeBenefitTestFixture fixture = new MrLifeBenefitTestFixture();

    @Test
    @DisplayName("대형마트는 10% 할인, 1회 5만원 인정, 30만원 실적 구간 월 3천원 한도를 적용한다")
    void calculatesWeekendMartDiscount() {
        BenefitCalculationResult result = fixture.calculator.calculate(fixture.weekendMartRule("3000", "0", 0, 0),
                fixture.context("70000", "0", "350000", "2026-08-01T14:00:00",
                        MrLifeBenefitTestFixture.OFFLINE_SHOPPING));

        fixture.assertApplied(result, "5000", "3000", "0");
    }

    @Test
    @DisplayName("대형마트는 1회 5만원까지만 혜택 기준금액으로 인정한다")
    void capsWeekendMartTransactionAmount() {
        fixture.assertApplied(fixture.calculator.calculate(fixture.weekendMartRule("10000", "0", 0, 0),
                fixture.context("100000", "0", "300000", "2026-08-01T00:00:00",
                        MrLifeBenefitTestFixture.OFFLINE_SHOPPING)), "5000", "5000", "5000");
    }

    @Test
    @DisplayName("주유소는 1회 10만원 인정 한도에 맞춰 사용량을 비례 계산한다")
    void capsFuelRewardByTransactionAmount() {
        BenefitCalculationResult result = fixture.calculator.calculate(fixture.fuelRule("3000", "0"),
                fixture.context("120000", "48", "300000", "2026-08-01T14:00:00",
                        MrLifeBenefitTestFixture.FUEL_CAR));

        fixture.assertApplied(result, "2400", "2400", "600");
    }

    @Test
    @DisplayName("주말 경계값은 토요일 00시 포함, 일요일 23시 59분 59초 포함으로 판단한다")
    void validatesWeekendBoundaries() {
        BenefitRule rule = fixture.weekendMartRule("10000", "0", 0, 0);

        assertWeekendRejected(rule, "2026-07-31T23:59:59");
        fixture.assertApplied(fixture.calculator.calculate(rule,
                fixture.context("50000", "0", "300000", "2026-08-01T00:00:00",
                        MrLifeBenefitTestFixture.OFFLINE_SHOPPING)), "5000", "5000", "5000");
        fixture.assertApplied(fixture.calculator.calculate(rule,
                fixture.context("50000", "0", "300000", "2026-08-02T23:59:59",
                        MrLifeBenefitTestFixture.OFFLINE_SHOPPING)), "5000", "5000", "5000");
        assertWeekendRejected(rule, "2026-05-27T12:00:00");
    }

    @Test
    @DisplayName("LPG 충전과 마트 내 상품권 구매처럼 제외 가맹점은 적용하지 않는다")
    void filtersWeekendMerchantEligibility() {
        fixture.assertRejected(fixture.calculator.calculate(fixture.fuelRule("3000", "0"),
                fixture.context("10000", "0", "300000", "2026-08-01T14:00:00",
                        MrLifeBenefitTestFixture.FUEL_CAR, false, 0, 0, false, true)),
                BenefitRejectionReason.MERCHANT_NOT_ELIGIBLE);
        fixture.assertRejected(fixture.calculator.calculate(fixture.weekendMartRule("10000", "0", 0, 0),
                fixture.context("10000", "0", "300000", "2026-08-01T14:00:00",
                        MrLifeBenefitTestFixture.OFFLINE_SHOPPING, false, 0, 0, false, true)),
                BenefitRejectionReason.MERCHANT_NOT_ELIGIBLE);
    }

    @Test
    @DisplayName("조건 미충족 상태에서 월 한도를 초과 사용한 경우 잔여 한도는 0원으로 반환한다")
    void returnsZeroRemainingLimitWhenAlreadyExceededLimit() {
        BenefitCalculationResult result = fixture.calculator.calculate(fixture.weekendMartRule("3000", "4000", 0, 0),
                fixture.context("50000", "0", "300000", "2026-07-27T14:00:00",
                        MrLifeBenefitTestFixture.OFFLINE_SHOPPING));

        fixture.assertRejected(result, BenefitRejectionReason.CONDITION_NOT_MET);
        fixture.assertBigDecimalEquals("0", result.remainingLimitValue());
    }

    private void assertWeekendRejected(BenefitRule rule, String approvedAt) {
        fixture.assertRejected(fixture.calculator.calculate(rule,
                fixture.context("10000", "0", "300000", approvedAt,
                        MrLifeBenefitTestFixture.OFFLINE_SHOPPING)), BenefitRejectionReason.CONDITION_NOT_MET);
    }
}
