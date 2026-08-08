package com.moca.mocabe.domain.benefit.calculation.card.generated.credit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moca.mocabe.domain.benefit.calculation.card.CardBenefitTestFixture;

import com.moca.mocabe.domain.benefit.model.BenefitCalculationResult;
import com.moca.mocabe.domain.benefit.model.BenefitRule;
import com.moca.mocabe.domain.benefit.type.BenefitBasis;
import com.moca.mocabe.domain.benefit.type.BenefitPromotionCondition;
import com.moca.mocabe.domain.benefit.type.BenefitType;
import com.moca.mocabe.domain.benefit.type.RewardUnit;
import com.moca.mocabe.domain.benefit.type.BenefitRejectionReason;


@DisplayName("신용 93위 American Express The Platinum Card®Edition2")
class CreditRank093Card2664BenefitTest {

    private final CardBenefitTestFixture fixture = new CardBenefitTestFixture();

    @Test
    @DisplayName("적립: 온라인·간편결제 범위 제외 - 온라인·앱·배달·간편결제·자동납부 전용 혜택")
    void classifiesBenefit001() {
        // 계산하지 않는 상세도 누락하지 않고 원본 분류 상태와 제외·검토 사유를 고정한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "2664",
                /* 카드 내 혜택 순번 */ 1,
                /* benefit_title */ "적립",
                /* 계산 지원 상태 */ "ONLINE_OR_INDIRECT_EXCLUDED",
                /* 분류 사유 */ "온라인·앱·배달·간편결제·자동납부 전용 혜택");
    }

    @Test
    @DisplayName("바우처: 정보성 혜택 - 바우처·수수료·무이자할부 등 결제금액 역산 대상이 아닌 혜택")
    void classifiesBenefit002() {
        // 계산하지 않는 상세도 누락하지 않고 원본 분류 상태와 제외·검토 사유를 고정한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "2664",
                /* 카드 내 혜택 순번 */ 2,
                /* benefit_title */ "바우처",
                /* 계산 지원 상태 */ "INFORMATION_ONLY",
                /* 분류 사유 */ "바우처·수수료·무이자할부 등 결제금액 역산 대상이 아닌 혜택");
    }

    @Test
    @DisplayName("프리미엄 서비스: 정보성 혜택 - 바우처·수수료·무이자할부 등 결제금액 역산 대상이 아닌 혜택")
    void classifiesBenefit003() {
        // 계산하지 않는 상세도 누락하지 않고 원본 분류 상태와 제외·검토 사유를 고정한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "2664",
                /* 카드 내 혜택 순번 */ 3,
                /* benefit_title */ "프리미엄 서비스",
                /* 계산 지원 상태 */ "INFORMATION_ONLY",
                /* 분류 사유 */ "바우처·수수료·무이자할부 등 결제금액 역산 대상이 아닌 혜택");
    }

    @Test
    @DisplayName("공항라운지: 정보성 혜택 - 바우처·수수료·무이자할부 등 결제금액 역산 대상이 아닌 혜택")
    void classifiesBenefit004() {
        // 계산하지 않는 상세도 누락하지 않고 원본 분류 상태와 제외·검토 사유를 고정한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "2664",
                /* 카드 내 혜택 순번 */ 4,
                /* benefit_title */ "공항라운지",
                /* 계산 지원 상태 */ "INFORMATION_ONLY",
                /* 분류 사유 */ "바우처·수수료·무이자할부 등 결제금액 역산 대상이 아닌 혜택");
    }

    @Test
    @DisplayName("프리미엄 서비스: 정보성 혜택 - 바우처·수수료·무이자할부 등 결제금액 역산 대상이 아닌 혜택")
    void classifiesBenefit005() {
        // 계산하지 않는 상세도 누락하지 않고 원본 분류 상태와 제외·검토 사유를 고정한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "2664",
                /* 카드 내 혜택 순번 */ 5,
                /* benefit_title */ "프리미엄 서비스",
                /* 계산 지원 상태 */ "INFORMATION_ONLY",
                /* 분류 사유 */ "바우처·수수료·무이자할부 등 결제금액 역산 대상이 아닌 혜택");
    }

    @Test
    @DisplayName("프리미엄 서비스: 30000원 정액 할인 정상 적용")
    void appliesBenefit006() {
        // 테스트에 사용한 계산 규칙이 카드고릴라의 해당 혜택 상세에서 만들어졌는지 확인한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "2664",
                /* 카드 내 혜택 순번 */ 6,
                /* benefit_title */ "프리미엄 서비스",
                /* 계산 지원 상태 */ "DIRECT_OFFLINE_CALCULABLE",
                /* 분류 사유 */ "직접 카드 결제 혜택 산식 계산 가능");

        // 카드 혜택 룰과 현재 결제 상황을 조합해 예상 혜택을 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit006Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ ""));

        // 월 한도 반영 전 혜택, 실제 적용 혜택, 계산 후 남은 월 한도를 차례로 검증한다.
        fixture.assertApplied(
                result,
                /* 월 한도 반영 전 혜택 */ "30000",
                /* 실제 적용 혜택 */ "30000",
                /* 남은 월 한도 */ "0");
    }

    @Test
    @DisplayName("프리미엄 서비스: 전월 실적 500,000원보다 1원 적으면 적용하지 않는다")
    void rejectsBenefit006WhenPerformanceIsOneWonBelowRequirement() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit006Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "499999",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ ""));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.PERFORMANCE_NOT_MET);
    }

    @Test
    @DisplayName("프리미엄 서비스: 전월 실적 500,000원부터 적용한다")
    void appliesBenefit006WhenPerformanceEqualsRequirement() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit006Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "500000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ ""));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("프리미엄 서비스: 신규 발급 유예기간에는 전월 실적 0원도 적용한다")
    void appliesBenefit006WhenNewMemberGracePeriodIsActive() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit006Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "0",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "",
                        /* 신규 발급 실적 유예 여부 */ true,
                        /* 오늘 이미 사용한 횟수 */ 0,
                        /* 이번 달 이미 사용한 횟수 */ 0,
                        /* 대상 가맹점 여부 */ true,
                        /* 지정 결제 채널 여부 */ true));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("프리미엄 서비스: 월 1회 한도의 마지막 허용 거래는 적용한다")
    void appliesBenefit006WhenMonthlyUsageIsLastAllowed() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit006Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ ""));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("프리미엄 서비스: 월 1회 사용 후에는 적용하지 않는다")
    void rejectsBenefit006WhenMonthlyUsageLimitIsExhausted() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit006Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "",
                        /* 신규 발급 실적 유예 여부 */ false,
                        /* 오늘 이미 사용한 횟수 */ 0,
                        /* 이번 달 이미 사용한 횟수 */ 1,
                        /* 대상 가맹점 여부 */ true,
                        /* 지정 결제 채널 여부 */ true));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.FREQUENCY_LIMIT_EXHAUSTED);
    }

    @Test
    @DisplayName("프리미엄 서비스: 정액 할인은 결제금액을 초과하지 않는다")
    void capsBenefit006FixedDiscountAtPaymentAmount() {
        // 정액 할인액보다 작은 결제금액으로 결제금액 초과 할인 방지 정책을 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit006Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "1",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ ""));

        // 원화 할인은 실제 결제금액까지만 적용되어야 한다.
        fixture.assertApplied(result);
        fixture.assertBigDecimalEquals("1", result.rawRewardValue());
    }

    @Test
    @DisplayName("프리미엄 서비스: 정보성 혜택 - 바우처·수수료·무이자할부 등 결제금액 역산 대상이 아닌 혜택")
    void classifiesBenefit007() {
        // 계산하지 않는 상세도 누락하지 않고 원본 분류 상태와 제외·검토 사유를 고정한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "2664",
                /* 카드 내 혜택 순번 */ 7,
                /* benefit_title */ "프리미엄 서비스",
                /* 계산 지원 상태 */ "INFORMATION_ONLY",
                /* 분류 사유 */ "바우처·수수료·무이자할부 등 결제금액 역산 대상이 아닌 혜택");
    }

    @Test
    @DisplayName("프리미엄 서비스: 정보성 혜택 - 바우처·수수료·무이자할부 등 결제금액 역산 대상이 아닌 혜택")
    void classifiesBenefit008() {
        // 계산하지 않는 상세도 누락하지 않고 원본 분류 상태와 제외·검토 사유를 고정한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "2664",
                /* 카드 내 혜택 순번 */ 8,
                /* benefit_title */ "프리미엄 서비스",
                /* 계산 지원 상태 */ "INFORMATION_ONLY",
                /* 분류 사유 */ "바우처·수수료·무이자할부 등 결제금액 역산 대상이 아닌 혜택");
    }

    /**
     * benefit_title=프리미엄 서비스의 계산 조건을 만든다.
     *
     * @param usedMonthlyValue 이번 달에 이미 적용받은 혜택 누적값
     * @return 금액·실적·한도·횟수·적용 조건이 구조화된 혜택 룰
     */
    private BenefitRule benefit006Rule(String usedMonthlyValue) {
        return fixture.rule(
                /* 룰 식별자 */ "card-2664-benefit-6",
                /* 할인·캐시백·포인트·마일리지 구분 */ BenefitType.DISCOUNT,
                /* 정률·정액·결제 단위·사용량 단위 구분 */ BenefitBasis.FIXED,
                /* 혜택 결과 단위 */ RewardUnit.KRW,
                /* 정률 계산 비율 */ "0",
                /* 정액 또는 단위당 혜택값 */ "30000",
                /* 단위 적립 기준 결제금액 */ "0",
                /* 1회 혜택 인정금액 상한 */ "0",
                /* 혜택 적용 최소 결제금액 */ "0",
                /* 필요한 전월 실적 */ "500000",
                /* 월 혜택 한도 */ "0",
                /* 이번 달에 이미 사용한 혜택 */ usedMonthlyValue,
                /* 시간·요일 조건 */ BenefitPromotionCondition.NONE,
                /* 적용 MOCA 카테고리 */ "",
                /* 일 사용 횟수 한도 */ 0,
                /* 월 사용 횟수 한도 */ 1,
                /* 대상 가맹점 확인 필요 여부 */ false,
                /* 지정 결제 채널 확인 필요 여부 */ false);
    }
}
