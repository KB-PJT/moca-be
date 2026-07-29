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


@DisplayName("신용 1위 신한카드 Mr.Life")
class CreditRank001Card13BenefitTest {

    private static final String NOT_MATCHED_CATEGORY = "__NOT_MATCHED__";

    private final CardBenefitTestFixture fixture = new CardBenefitTestFixture();

    @Test
    @DisplayName("공과금: 온라인·간편결제 범위 제외 - 온라인·앱·배달·간편결제·자동납부 전용 혜택")
    void classifiesBenefit001() {
        // 계산하지 않는 상세도 누락하지 않고 원본 분류 상태와 제외·검토 사유를 고정한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "13",
                /* 카드 내 혜택 순번 */ 1,
                /* benefit_title */ "공과금",
                /* 계산 지원 상태 */ "ONLINE_OR_INDIRECT_EXCLUDED",
                /* 분류 사유 */ "온라인·앱·배달·간편결제·자동납부 전용 혜택");
    }

    @Test
    @DisplayName("편의점: 10% 할인 정상 적용")
    void appliesBenefit002() {
        // 테스트에 사용한 계산 규칙이 카드고릴라의 해당 혜택 상세에서 만들어졌는지 확인한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "13",
                /* 카드 내 혜택 순번 */ 2,
                /* benefit_title */ "편의점",
                /* 계산 지원 상태 */ "DIRECT_OFFLINE_CALCULABLE",
                /* 분류 사유 */ "직접 카드 결제 혜택 산식 계산 가능");

        // 카드 혜택 룰과 현재 결제 상황을 조합해 예상 혜택을 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit002Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "CONVENIENCE_STORE"));

        // 월 한도 반영 전 혜택, 실제 적용 혜택, 계산 후 남은 월 한도를 차례로 검증한다.
        fixture.assertApplied(
                result,
                /* 월 한도 반영 전 혜택 */ "1000",
                /* 실제 적용 혜택 */ "1000",
                /* 남은 월 한도 */ "9000");
    }

    @Test
    @DisplayName("편의점: 카테고리가 일치하지 않으면 적용하지 않는다")
    void rejectsBenefit002WhenCategoryIsNotMatched() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit002Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ NOT_MATCHED_CATEGORY));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.CATEGORY_NOT_MATCHED);
    }

    @Test
    @DisplayName("편의점: 전월 실적 300,000원보다 1원 적으면 적용하지 않는다")
    void rejectsBenefit002WhenPerformanceIsOneWonBelowRequirement() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit002Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "299999",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "CONVENIENCE_STORE"));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.PERFORMANCE_NOT_MET);
    }

    @Test
    @DisplayName("편의점: 전월 실적 300,000원부터 적용한다")
    void appliesBenefit002WhenPerformanceEqualsRequirement() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit002Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "300000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "CONVENIENCE_STORE"));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("편의점: 신규 발급 유예기간에는 전월 실적 0원도 적용한다")
    void appliesBenefit002WhenNewMemberGracePeriodIsActive() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit002Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "0",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "CONVENIENCE_STORE",
                        /* 신규 발급 실적 유예 여부 */ true,
                        /* 오늘 이미 사용한 횟수 */ 0,
                        /* 이번 달 이미 사용한 횟수 */ 0,
                        /* 대상 가맹점 여부 */ true,
                        /* 지정 결제 채널 여부 */ true));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("편의점: 1회 인정금액 10,000원 초과분은 혜택에서 제외한다")
    void capsBenefit002BenefitBaseAmount() {
        // 1회 인정금액과 정확히 같은 결제의 혜택을 계산한다.
        BenefitCalculationResult atLimit = fixture.calculator.calculate(
                benefit002Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "10000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "CONVENIENCE_STORE"));

        // 인정금액보다 1원 큰 결제로 초과분이 계산 기준에서 제외되는지 확인한다.
        BenefitCalculationResult overLimit = fixture.calculator.calculate(
                benefit002Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "10001",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "CONVENIENCE_STORE"));

        // 두 결제의 혜택이 같으면 1회 인정금액 상한이 정상 적용된 것이다.
        fixture.assertApplied(atLimit);
        fixture.assertApplied(overLimit);
        fixture.assertBigDecimalEquals(atLimit.rawRewardValue(), overLimit.rawRewardValue());
    }

    @Test
    @DisplayName("편의점: 월 한도가 1원 남았으면 1원만 적용한다")
    void appliesOnlyRemainingMonthlyLimitForBenefit002() {
        // 월 한도에서 이미 사용한 혜택을 빼고 1원만 남은 상태로 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit002Rule(/* 이번 달에 이미 사용한 혜택 */ "9999"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "CONVENIENCE_STORE"));

        // 계산 혜택이 더 크더라도 남은 1원만 적용되고 월 한도는 0원이 되어야 한다.
        fixture.assertApplied(result);
        fixture.assertBigDecimalEquals("1", result.appliedRewardValue());
        fixture.assertBigDecimalEquals("0", result.remainingLimitValue());
    }

    @Test
    @DisplayName("편의점: 월 한도를 모두 사용했으면 적용하지 않는다")
    void rejectsBenefit002WhenMonthlyLimitIsExhausted() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit002Rule(/* 이번 달에 이미 사용한 혜택 */ "10000"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "CONVENIENCE_STORE"));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.MONTHLY_LIMIT_EXHAUSTED);
    }

    @Test
    @DisplayName("편의점: 일 1회 한도의 마지막 허용 거래는 적용한다")
    void appliesBenefit002WhenDailyUsageIsLastAllowed() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit002Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "CONVENIENCE_STORE"));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("편의점: 일 1회 사용 후에는 적용하지 않는다")
    void rejectsBenefit002WhenDailyUsageLimitIsExhausted() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit002Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "CONVENIENCE_STORE",
                        /* 신규 발급 실적 유예 여부 */ false,
                        /* 오늘 이미 사용한 횟수 */ 1,
                        /* 이번 달 이미 사용한 횟수 */ 0,
                        /* 대상 가맹점 여부 */ true,
                        /* 지정 결제 채널 여부 */ true));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.FREQUENCY_LIMIT_EXHAUSTED);
    }

    @Test
    @DisplayName("편의점: 월 5회 한도의 마지막 허용 거래는 적용한다")
    void appliesBenefit002WhenMonthlyUsageIsLastAllowed() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit002Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "CONVENIENCE_STORE",
                        /* 신규 발급 실적 유예 여부 */ false,
                        /* 오늘 이미 사용한 횟수 */ 0,
                        /* 이번 달 이미 사용한 횟수 */ 4,
                        /* 대상 가맹점 여부 */ true,
                        /* 지정 결제 채널 여부 */ true));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("편의점: 월 5회 사용 후에는 적용하지 않는다")
    void rejectsBenefit002WhenMonthlyUsageLimitIsExhausted() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit002Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "CONVENIENCE_STORE",
                        /* 신규 발급 실적 유예 여부 */ false,
                        /* 오늘 이미 사용한 횟수 */ 0,
                        /* 이번 달 이미 사용한 횟수 */ 5,
                        /* 대상 가맹점 여부 */ true,
                        /* 지정 결제 채널 여부 */ true));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.FREQUENCY_LIMIT_EXHAUSTED);
    }

    @Test
    @DisplayName("편의점: 정률 혜택의 원 미만 금액을 절사한다")
    void floorsBenefit002FractionalReward() {
        // 비율 계산 결과에 원 미만 소수가 생기는 결제금액으로 절사 정책을 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit002Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "10001",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "CONVENIENCE_STORE"));

        // 카드 혜택은 원 미만 금액을 올림하지 않고 버린 결과와 같아야 한다.
        fixture.assertApplied(result);
        fixture.assertBigDecimalEquals("1000", result.rawRewardValue());
    }

    @Test
    @DisplayName("병원/약국: 10% 할인 정상 적용")
    void appliesBenefit003() {
        // 테스트에 사용한 계산 규칙이 카드고릴라의 해당 혜택 상세에서 만들어졌는지 확인한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "13",
                /* 카드 내 혜택 순번 */ 3,
                /* benefit_title */ "병원/약국",
                /* 계산 지원 상태 */ "DIRECT_OFFLINE_CALCULABLE",
                /* 분류 사유 */ "직접 카드 결제 혜택 산식 계산 가능");

        // 카드 혜택 룰과 현재 결제 상황을 조합해 예상 혜택을 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit003Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "MEDICAL_BEAUTY"));

        // 월 한도 반영 전 혜택, 실제 적용 혜택, 계산 후 남은 월 한도를 차례로 검증한다.
        fixture.assertApplied(
                result,
                /* 월 한도 반영 전 혜택 */ "1000",
                /* 실제 적용 혜택 */ "1000",
                /* 남은 월 한도 */ "9000");
    }

    @Test
    @DisplayName("병원/약국: 카테고리가 일치하지 않으면 적용하지 않는다")
    void rejectsBenefit003WhenCategoryIsNotMatched() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit003Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ NOT_MATCHED_CATEGORY));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.CATEGORY_NOT_MATCHED);
    }

    @Test
    @DisplayName("병원/약국: 전월 실적 300,000원보다 1원 적으면 적용하지 않는다")
    void rejectsBenefit003WhenPerformanceIsOneWonBelowRequirement() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit003Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "299999",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "MEDICAL_BEAUTY"));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.PERFORMANCE_NOT_MET);
    }

    @Test
    @DisplayName("병원/약국: 전월 실적 300,000원부터 적용한다")
    void appliesBenefit003WhenPerformanceEqualsRequirement() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit003Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "300000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "MEDICAL_BEAUTY"));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("병원/약국: 신규 발급 유예기간에는 전월 실적 0원도 적용한다")
    void appliesBenefit003WhenNewMemberGracePeriodIsActive() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit003Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "0",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "MEDICAL_BEAUTY",
                        /* 신규 발급 실적 유예 여부 */ true,
                        /* 오늘 이미 사용한 횟수 */ 0,
                        /* 이번 달 이미 사용한 횟수 */ 0,
                        /* 대상 가맹점 여부 */ true,
                        /* 지정 결제 채널 여부 */ true));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("병원/약국: 1회 인정금액 10,000원 초과분은 혜택에서 제외한다")
    void capsBenefit003BenefitBaseAmount() {
        // 1회 인정금액과 정확히 같은 결제의 혜택을 계산한다.
        BenefitCalculationResult atLimit = fixture.calculator.calculate(
                benefit003Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "10000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "MEDICAL_BEAUTY"));

        // 인정금액보다 1원 큰 결제로 초과분이 계산 기준에서 제외되는지 확인한다.
        BenefitCalculationResult overLimit = fixture.calculator.calculate(
                benefit003Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "10001",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "MEDICAL_BEAUTY"));

        // 두 결제의 혜택이 같으면 1회 인정금액 상한이 정상 적용된 것이다.
        fixture.assertApplied(atLimit);
        fixture.assertApplied(overLimit);
        fixture.assertBigDecimalEquals(atLimit.rawRewardValue(), overLimit.rawRewardValue());
    }

    @Test
    @DisplayName("병원/약국: 월 한도가 1원 남았으면 1원만 적용한다")
    void appliesOnlyRemainingMonthlyLimitForBenefit003() {
        // 월 한도에서 이미 사용한 혜택을 빼고 1원만 남은 상태로 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit003Rule(/* 이번 달에 이미 사용한 혜택 */ "9999"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "MEDICAL_BEAUTY"));

        // 계산 혜택이 더 크더라도 남은 1원만 적용되고 월 한도는 0원이 되어야 한다.
        fixture.assertApplied(result);
        fixture.assertBigDecimalEquals("1", result.appliedRewardValue());
        fixture.assertBigDecimalEquals("0", result.remainingLimitValue());
    }

    @Test
    @DisplayName("병원/약국: 월 한도를 모두 사용했으면 적용하지 않는다")
    void rejectsBenefit003WhenMonthlyLimitIsExhausted() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit003Rule(/* 이번 달에 이미 사용한 혜택 */ "10000"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "MEDICAL_BEAUTY"));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.MONTHLY_LIMIT_EXHAUSTED);
    }

    @Test
    @DisplayName("병원/약국: 일 1회 한도의 마지막 허용 거래는 적용한다")
    void appliesBenefit003WhenDailyUsageIsLastAllowed() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit003Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "MEDICAL_BEAUTY"));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("병원/약국: 일 1회 사용 후에는 적용하지 않는다")
    void rejectsBenefit003WhenDailyUsageLimitIsExhausted() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit003Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "MEDICAL_BEAUTY",
                        /* 신규 발급 실적 유예 여부 */ false,
                        /* 오늘 이미 사용한 횟수 */ 1,
                        /* 이번 달 이미 사용한 횟수 */ 0,
                        /* 대상 가맹점 여부 */ true,
                        /* 지정 결제 채널 여부 */ true));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.FREQUENCY_LIMIT_EXHAUSTED);
    }

    @Test
    @DisplayName("병원/약국: 월 5회 한도의 마지막 허용 거래는 적용한다")
    void appliesBenefit003WhenMonthlyUsageIsLastAllowed() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit003Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "MEDICAL_BEAUTY",
                        /* 신규 발급 실적 유예 여부 */ false,
                        /* 오늘 이미 사용한 횟수 */ 0,
                        /* 이번 달 이미 사용한 횟수 */ 4,
                        /* 대상 가맹점 여부 */ true,
                        /* 지정 결제 채널 여부 */ true));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("병원/약국: 월 5회 사용 후에는 적용하지 않는다")
    void rejectsBenefit003WhenMonthlyUsageLimitIsExhausted() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit003Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "MEDICAL_BEAUTY",
                        /* 신규 발급 실적 유예 여부 */ false,
                        /* 오늘 이미 사용한 횟수 */ 0,
                        /* 이번 달 이미 사용한 횟수 */ 5,
                        /* 대상 가맹점 여부 */ true,
                        /* 지정 결제 채널 여부 */ true));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.FREQUENCY_LIMIT_EXHAUSTED);
    }

    @Test
    @DisplayName("병원/약국: 정률 혜택의 원 미만 금액을 절사한다")
    void floorsBenefit003FractionalReward() {
        // 비율 계산 결과에 원 미만 소수가 생기는 결제금액으로 절사 정책을 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit003Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "10001",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "MEDICAL_BEAUTY"));

        // 카드 혜택은 원 미만 금액을 올림하지 않고 버린 결과와 같아야 한다.
        fixture.assertApplied(result);
        fixture.assertBigDecimalEquals("1000", result.rawRewardValue());
    }

    @Test
    @DisplayName("생활: 10% 할인 정상 적용")
    void appliesBenefit004() {
        // 테스트에 사용한 계산 규칙이 카드고릴라의 해당 혜택 상세에서 만들어졌는지 확인한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "13",
                /* 카드 내 혜택 순번 */ 4,
                /* benefit_title */ "생활",
                /* 계산 지원 상태 */ "DIRECT_OFFLINE_CALCULABLE",
                /* 분류 사유 */ "직접 카드 결제 혜택 산식 계산 가능");

        // 카드 혜택 룰과 현재 결제 상황을 조합해 예상 혜택을 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit004Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "LAUNDRY"));

        // 월 한도 반영 전 혜택, 실제 적용 혜택, 계산 후 남은 월 한도를 차례로 검증한다.
        fixture.assertApplied(
                result,
                /* 월 한도 반영 전 혜택 */ "1000",
                /* 실제 적용 혜택 */ "1000",
                /* 남은 월 한도 */ "9000");
    }

    @Test
    @DisplayName("생활: 카테고리가 일치하지 않으면 적용하지 않는다")
    void rejectsBenefit004WhenCategoryIsNotMatched() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit004Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ NOT_MATCHED_CATEGORY));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.CATEGORY_NOT_MATCHED);
    }

    @Test
    @DisplayName("생활: 전월 실적 300,000원보다 1원 적으면 적용하지 않는다")
    void rejectsBenefit004WhenPerformanceIsOneWonBelowRequirement() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit004Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "299999",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "LAUNDRY"));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.PERFORMANCE_NOT_MET);
    }

    @Test
    @DisplayName("생활: 전월 실적 300,000원부터 적용한다")
    void appliesBenefit004WhenPerformanceEqualsRequirement() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit004Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "300000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "LAUNDRY"));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("생활: 신규 발급 유예기간에는 전월 실적 0원도 적용한다")
    void appliesBenefit004WhenNewMemberGracePeriodIsActive() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit004Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "0",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "LAUNDRY",
                        /* 신규 발급 실적 유예 여부 */ true,
                        /* 오늘 이미 사용한 횟수 */ 0,
                        /* 이번 달 이미 사용한 횟수 */ 0,
                        /* 대상 가맹점 여부 */ true,
                        /* 지정 결제 채널 여부 */ true));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("생활: 1회 인정금액 10,000원 초과분은 혜택에서 제외한다")
    void capsBenefit004BenefitBaseAmount() {
        // 1회 인정금액과 정확히 같은 결제의 혜택을 계산한다.
        BenefitCalculationResult atLimit = fixture.calculator.calculate(
                benefit004Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "10000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "LAUNDRY"));

        // 인정금액보다 1원 큰 결제로 초과분이 계산 기준에서 제외되는지 확인한다.
        BenefitCalculationResult overLimit = fixture.calculator.calculate(
                benefit004Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "10001",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "LAUNDRY"));

        // 두 결제의 혜택이 같으면 1회 인정금액 상한이 정상 적용된 것이다.
        fixture.assertApplied(atLimit);
        fixture.assertApplied(overLimit);
        fixture.assertBigDecimalEquals(atLimit.rawRewardValue(), overLimit.rawRewardValue());
    }

    @Test
    @DisplayName("생활: 월 한도가 1원 남았으면 1원만 적용한다")
    void appliesOnlyRemainingMonthlyLimitForBenefit004() {
        // 월 한도에서 이미 사용한 혜택을 빼고 1원만 남은 상태로 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit004Rule(/* 이번 달에 이미 사용한 혜택 */ "9999"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "LAUNDRY"));

        // 계산 혜택이 더 크더라도 남은 1원만 적용되고 월 한도는 0원이 되어야 한다.
        fixture.assertApplied(result);
        fixture.assertBigDecimalEquals("1", result.appliedRewardValue());
        fixture.assertBigDecimalEquals("0", result.remainingLimitValue());
    }

    @Test
    @DisplayName("생활: 월 한도를 모두 사용했으면 적용하지 않는다")
    void rejectsBenefit004WhenMonthlyLimitIsExhausted() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit004Rule(/* 이번 달에 이미 사용한 혜택 */ "10000"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "LAUNDRY"));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.MONTHLY_LIMIT_EXHAUSTED);
    }

    @Test
    @DisplayName("생활: 일 1회 한도의 마지막 허용 거래는 적용한다")
    void appliesBenefit004WhenDailyUsageIsLastAllowed() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit004Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "LAUNDRY"));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("생활: 일 1회 사용 후에는 적용하지 않는다")
    void rejectsBenefit004WhenDailyUsageLimitIsExhausted() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit004Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "LAUNDRY",
                        /* 신규 발급 실적 유예 여부 */ false,
                        /* 오늘 이미 사용한 횟수 */ 1,
                        /* 이번 달 이미 사용한 횟수 */ 0,
                        /* 대상 가맹점 여부 */ true,
                        /* 지정 결제 채널 여부 */ true));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.FREQUENCY_LIMIT_EXHAUSTED);
    }

    @Test
    @DisplayName("생활: 월 5회 한도의 마지막 허용 거래는 적용한다")
    void appliesBenefit004WhenMonthlyUsageIsLastAllowed() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit004Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "LAUNDRY",
                        /* 신규 발급 실적 유예 여부 */ false,
                        /* 오늘 이미 사용한 횟수 */ 0,
                        /* 이번 달 이미 사용한 횟수 */ 4,
                        /* 대상 가맹점 여부 */ true,
                        /* 지정 결제 채널 여부 */ true));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("생활: 월 5회 사용 후에는 적용하지 않는다")
    void rejectsBenefit004WhenMonthlyUsageLimitIsExhausted() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit004Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "LAUNDRY",
                        /* 신규 발급 실적 유예 여부 */ false,
                        /* 오늘 이미 사용한 횟수 */ 0,
                        /* 이번 달 이미 사용한 횟수 */ 5,
                        /* 대상 가맹점 여부 */ true,
                        /* 지정 결제 채널 여부 */ true));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.FREQUENCY_LIMIT_EXHAUSTED);
    }

    @Test
    @DisplayName("생활: 정률 혜택의 원 미만 금액을 절사한다")
    void floorsBenefit004FractionalReward() {
        // 비율 계산 결과에 원 미만 소수가 생기는 결제금액으로 절사 정책을 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit004Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "10001",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "LAUNDRY"));

        // 카드 혜택은 원 미만 금액을 올림하지 않고 버린 결과와 같아야 한다.
        fixture.assertApplied(result);
        fixture.assertBigDecimalEquals("1000", result.rawRewardValue());
    }

    @Test
    @DisplayName("온라인쇼핑: 온라인·간편결제 범위 제외 - 온라인·앱·배달·간편결제·자동납부 전용 혜택")
    void classifiesBenefit005() {
        // 계산하지 않는 상세도 누락하지 않고 원본 분류 상태와 제외·검토 사유를 고정한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "13",
                /* 카드 내 혜택 순번 */ 5,
                /* benefit_title */ "온라인쇼핑",
                /* 계산 지원 상태 */ "ONLINE_OR_INDIRECT_EXCLUDED",
                /* 분류 사유 */ "온라인·앱·배달·간편결제·자동납부 전용 혜택");
    }

    @Test
    @DisplayName("택시: 10% 할인 정상 적용")
    void appliesBenefit006() {
        // 테스트에 사용한 계산 규칙이 카드고릴라의 해당 혜택 상세에서 만들어졌는지 확인한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "13",
                /* 카드 내 혜택 순번 */ 6,
                /* benefit_title */ "택시",
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
                        /* MOCA 가맹점 카테고리 */ "TAXI_MOBILITY"));

        // 월 한도 반영 전 혜택, 실제 적용 혜택, 계산 후 남은 월 한도를 차례로 검증한다.
        fixture.assertApplied(
                result,
                /* 월 한도 반영 전 혜택 */ "1000",
                /* 실제 적용 혜택 */ "1000",
                /* 남은 월 한도 */ "9000");
    }

    @Test
    @DisplayName("택시: 카테고리가 일치하지 않으면 적용하지 않는다")
    void rejectsBenefit006WhenCategoryIsNotMatched() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit006Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ NOT_MATCHED_CATEGORY));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.CATEGORY_NOT_MATCHED);
    }

    @Test
    @DisplayName("택시: 전월 실적 300,000원보다 1원 적으면 적용하지 않는다")
    void rejectsBenefit006WhenPerformanceIsOneWonBelowRequirement() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit006Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "299999",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "TAXI_MOBILITY"));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.PERFORMANCE_NOT_MET);
    }

    @Test
    @DisplayName("택시: 전월 실적 300,000원부터 적용한다")
    void appliesBenefit006WhenPerformanceEqualsRequirement() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit006Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "300000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "TAXI_MOBILITY"));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("택시: 신규 발급 유예기간에는 전월 실적 0원도 적용한다")
    void appliesBenefit006WhenNewMemberGracePeriodIsActive() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit006Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "0",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "TAXI_MOBILITY",
                        /* 신규 발급 실적 유예 여부 */ true,
                        /* 오늘 이미 사용한 횟수 */ 0,
                        /* 이번 달 이미 사용한 횟수 */ 0,
                        /* 대상 가맹점 여부 */ true,
                        /* 지정 결제 채널 여부 */ true));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("택시: 1회 인정금액 10,000원 초과분은 혜택에서 제외한다")
    void capsBenefit006BenefitBaseAmount() {
        // 1회 인정금액과 정확히 같은 결제의 혜택을 계산한다.
        BenefitCalculationResult atLimit = fixture.calculator.calculate(
                benefit006Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "10000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "TAXI_MOBILITY"));

        // 인정금액보다 1원 큰 결제로 초과분이 계산 기준에서 제외되는지 확인한다.
        BenefitCalculationResult overLimit = fixture.calculator.calculate(
                benefit006Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "10001",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "TAXI_MOBILITY"));

        // 두 결제의 혜택이 같으면 1회 인정금액 상한이 정상 적용된 것이다.
        fixture.assertApplied(atLimit);
        fixture.assertApplied(overLimit);
        fixture.assertBigDecimalEquals(atLimit.rawRewardValue(), overLimit.rawRewardValue());
    }

    @Test
    @DisplayName("택시: 월 한도가 1원 남았으면 1원만 적용한다")
    void appliesOnlyRemainingMonthlyLimitForBenefit006() {
        // 월 한도에서 이미 사용한 혜택을 빼고 1원만 남은 상태로 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit006Rule(/* 이번 달에 이미 사용한 혜택 */ "9999"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "TAXI_MOBILITY"));

        // 계산 혜택이 더 크더라도 남은 1원만 적용되고 월 한도는 0원이 되어야 한다.
        fixture.assertApplied(result);
        fixture.assertBigDecimalEquals("1", result.appliedRewardValue());
        fixture.assertBigDecimalEquals("0", result.remainingLimitValue());
    }

    @Test
    @DisplayName("택시: 월 한도를 모두 사용했으면 적용하지 않는다")
    void rejectsBenefit006WhenMonthlyLimitIsExhausted() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit006Rule(/* 이번 달에 이미 사용한 혜택 */ "10000"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "TAXI_MOBILITY"));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.MONTHLY_LIMIT_EXHAUSTED);
    }

    @Test
    @DisplayName("택시: 일 1회 한도의 마지막 허용 거래는 적용한다")
    void appliesBenefit006WhenDailyUsageIsLastAllowed() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit006Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "TAXI_MOBILITY"));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("택시: 일 1회 사용 후에는 적용하지 않는다")
    void rejectsBenefit006WhenDailyUsageLimitIsExhausted() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit006Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "TAXI_MOBILITY",
                        /* 신규 발급 실적 유예 여부 */ false,
                        /* 오늘 이미 사용한 횟수 */ 1,
                        /* 이번 달 이미 사용한 횟수 */ 0,
                        /* 대상 가맹점 여부 */ true,
                        /* 지정 결제 채널 여부 */ true));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.FREQUENCY_LIMIT_EXHAUSTED);
    }

    @Test
    @DisplayName("택시: 월 10회 한도의 마지막 허용 거래는 적용한다")
    void appliesBenefit006WhenMonthlyUsageIsLastAllowed() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit006Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "TAXI_MOBILITY",
                        /* 신규 발급 실적 유예 여부 */ false,
                        /* 오늘 이미 사용한 횟수 */ 0,
                        /* 이번 달 이미 사용한 횟수 */ 9,
                        /* 대상 가맹점 여부 */ true,
                        /* 지정 결제 채널 여부 */ true));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("택시: 월 10회 사용 후에는 적용하지 않는다")
    void rejectsBenefit006WhenMonthlyUsageLimitIsExhausted() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit006Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "TAXI_MOBILITY",
                        /* 신규 발급 실적 유예 여부 */ false,
                        /* 오늘 이미 사용한 횟수 */ 0,
                        /* 이번 달 이미 사용한 횟수 */ 10,
                        /* 대상 가맹점 여부 */ true,
                        /* 지정 결제 채널 여부 */ true));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.FREQUENCY_LIMIT_EXHAUSTED);
    }

    @Test
    @DisplayName("택시: 정률 혜택의 원 미만 금액을 절사한다")
    void floorsBenefit006FractionalReward() {
        // 비율 계산 결과에 원 미만 소수가 생기는 결제금액으로 절사 정책을 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit006Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "10001",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "TAXI_MOBILITY"));

        // 카드 혜택은 원 미만 금액을 올림하지 않고 버린 결과와 같아야 한다.
        fixture.assertApplied(result);
        fixture.assertBigDecimalEquals("1000", result.rawRewardValue());
    }

    @Test
    @DisplayName("푸드: 10% 할인 정상 적용")
    void appliesBenefit007() {
        // 테스트에 사용한 계산 규칙이 카드고릴라의 해당 혜택 상세에서 만들어졌는지 확인한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "13",
                /* 카드 내 혜택 순번 */ 7,
                /* benefit_title */ "푸드",
                /* 계산 지원 상태 */ "DIRECT_OFFLINE_CALCULABLE",
                /* 분류 사유 */ "직접 카드 결제 혜택 산식 계산 가능");

        // 카드 혜택 룰과 현재 결제 상황을 조합해 예상 혜택을 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit007Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "FOOD_DINING"));

        // 월 한도 반영 전 혜택, 실제 적용 혜택, 계산 후 남은 월 한도를 차례로 검증한다.
        fixture.assertApplied(
                result,
                /* 월 한도 반영 전 혜택 */ "1000",
                /* 실제 적용 혜택 */ "1000",
                /* 남은 월 한도 */ "9000");
    }

    @Test
    @DisplayName("푸드: 카테고리가 일치하지 않으면 적용하지 않는다")
    void rejectsBenefit007WhenCategoryIsNotMatched() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit007Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ NOT_MATCHED_CATEGORY));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.CATEGORY_NOT_MATCHED);
    }

    @Test
    @DisplayName("푸드: 전월 실적 300,000원보다 1원 적으면 적용하지 않는다")
    void rejectsBenefit007WhenPerformanceIsOneWonBelowRequirement() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit007Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "299999",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "FOOD_DINING"));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.PERFORMANCE_NOT_MET);
    }

    @Test
    @DisplayName("푸드: 전월 실적 300,000원부터 적용한다")
    void appliesBenefit007WhenPerformanceEqualsRequirement() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit007Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "300000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "FOOD_DINING"));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("푸드: 신규 발급 유예기간에는 전월 실적 0원도 적용한다")
    void appliesBenefit007WhenNewMemberGracePeriodIsActive() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit007Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "0",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "FOOD_DINING",
                        /* 신규 발급 실적 유예 여부 */ true,
                        /* 오늘 이미 사용한 횟수 */ 0,
                        /* 이번 달 이미 사용한 횟수 */ 0,
                        /* 대상 가맹점 여부 */ true,
                        /* 지정 결제 채널 여부 */ true));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("푸드: 1회 인정금액 10,000원 초과분은 혜택에서 제외한다")
    void capsBenefit007BenefitBaseAmount() {
        // 1회 인정금액과 정확히 같은 결제의 혜택을 계산한다.
        BenefitCalculationResult atLimit = fixture.calculator.calculate(
                benefit007Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "10000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "FOOD_DINING"));

        // 인정금액보다 1원 큰 결제로 초과분이 계산 기준에서 제외되는지 확인한다.
        BenefitCalculationResult overLimit = fixture.calculator.calculate(
                benefit007Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "10001",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "FOOD_DINING"));

        // 두 결제의 혜택이 같으면 1회 인정금액 상한이 정상 적용된 것이다.
        fixture.assertApplied(atLimit);
        fixture.assertApplied(overLimit);
        fixture.assertBigDecimalEquals(atLimit.rawRewardValue(), overLimit.rawRewardValue());
    }

    @Test
    @DisplayName("푸드: 월 한도가 1원 남았으면 1원만 적용한다")
    void appliesOnlyRemainingMonthlyLimitForBenefit007() {
        // 월 한도에서 이미 사용한 혜택을 빼고 1원만 남은 상태로 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit007Rule(/* 이번 달에 이미 사용한 혜택 */ "9999"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "FOOD_DINING"));

        // 계산 혜택이 더 크더라도 남은 1원만 적용되고 월 한도는 0원이 되어야 한다.
        fixture.assertApplied(result);
        fixture.assertBigDecimalEquals("1", result.appliedRewardValue());
        fixture.assertBigDecimalEquals("0", result.remainingLimitValue());
    }

    @Test
    @DisplayName("푸드: 월 한도를 모두 사용했으면 적용하지 않는다")
    void rejectsBenefit007WhenMonthlyLimitIsExhausted() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit007Rule(/* 이번 달에 이미 사용한 혜택 */ "10000"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "FOOD_DINING"));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.MONTHLY_LIMIT_EXHAUSTED);
    }

    @Test
    @DisplayName("푸드: 일 1회 한도의 마지막 허용 거래는 적용한다")
    void appliesBenefit007WhenDailyUsageIsLastAllowed() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit007Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "FOOD_DINING"));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("푸드: 일 1회 사용 후에는 적용하지 않는다")
    void rejectsBenefit007WhenDailyUsageLimitIsExhausted() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit007Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "FOOD_DINING",
                        /* 신규 발급 실적 유예 여부 */ false,
                        /* 오늘 이미 사용한 횟수 */ 1,
                        /* 이번 달 이미 사용한 횟수 */ 0,
                        /* 대상 가맹점 여부 */ true,
                        /* 지정 결제 채널 여부 */ true));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.FREQUENCY_LIMIT_EXHAUSTED);
    }

    @Test
    @DisplayName("푸드: 월 10회 한도의 마지막 허용 거래는 적용한다")
    void appliesBenefit007WhenMonthlyUsageIsLastAllowed() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit007Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "FOOD_DINING",
                        /* 신규 발급 실적 유예 여부 */ false,
                        /* 오늘 이미 사용한 횟수 */ 0,
                        /* 이번 달 이미 사용한 횟수 */ 9,
                        /* 대상 가맹점 여부 */ true,
                        /* 지정 결제 채널 여부 */ true));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("푸드: 월 10회 사용 후에는 적용하지 않는다")
    void rejectsBenefit007WhenMonthlyUsageLimitIsExhausted() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit007Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "FOOD_DINING",
                        /* 신규 발급 실적 유예 여부 */ false,
                        /* 오늘 이미 사용한 횟수 */ 0,
                        /* 이번 달 이미 사용한 횟수 */ 10,
                        /* 대상 가맹점 여부 */ true,
                        /* 지정 결제 채널 여부 */ true));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.FREQUENCY_LIMIT_EXHAUSTED);
    }

    @Test
    @DisplayName("푸드: 정률 혜택의 원 미만 금액을 절사한다")
    void floorsBenefit007FractionalReward() {
        // 비율 계산 결과에 원 미만 소수가 생기는 결제금액으로 절사 정책을 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit007Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "10001",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "FOOD_DINING"));

        // 카드 혜택은 원 미만 금액을 올림하지 않고 버린 결과와 같아야 한다.
        fixture.assertApplied(result);
        fixture.assertBigDecimalEquals("1000", result.rawRewardValue());
    }

    @Test
    @DisplayName("대형마트: 10% 할인 정상 적용")
    void appliesBenefit008() {
        // 테스트에 사용한 계산 규칙이 카드고릴라의 해당 혜택 상세에서 만들어졌는지 확인한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "13",
                /* 카드 내 혜택 순번 */ 8,
                /* benefit_title */ "대형마트",
                /* 계산 지원 상태 */ "DIRECT_OFFLINE_CALCULABLE",
                /* 분류 사유 */ "직접 카드 결제 혜택 산식 계산 가능");

        // 카드 혜택 룰과 현재 결제 상황을 조합해 예상 혜택을 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit008Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "OFFLINE_SHOPPING"));

        // 월 한도 반영 전 혜택, 실제 적용 혜택, 계산 후 남은 월 한도를 차례로 검증한다.
        fixture.assertApplied(
                result,
                /* 월 한도 반영 전 혜택 */ "5000",
                /* 실제 적용 혜택 */ "3000",
                /* 남은 월 한도 */ "0");
    }

    @Test
    @DisplayName("대형마트: 카테고리가 일치하지 않으면 적용하지 않는다")
    void rejectsBenefit008WhenCategoryIsNotMatched() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit008Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ NOT_MATCHED_CATEGORY));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.CATEGORY_NOT_MATCHED);
    }

    @Test
    @DisplayName("대형마트: 전월 실적 300,000원보다 1원 적으면 적용하지 않는다")
    void rejectsBenefit008WhenPerformanceIsOneWonBelowRequirement() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit008Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "299999",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "OFFLINE_SHOPPING"));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.PERFORMANCE_NOT_MET);
    }

    @Test
    @DisplayName("대형마트: 전월 실적 300,000원부터 적용한다")
    void appliesBenefit008WhenPerformanceEqualsRequirement() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit008Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "300000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "OFFLINE_SHOPPING"));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("대형마트: 신규 발급 유예기간에는 전월 실적 0원도 적용한다")
    void appliesBenefit008WhenNewMemberGracePeriodIsActive() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit008Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "0",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "OFFLINE_SHOPPING",
                        /* 신규 발급 실적 유예 여부 */ true,
                        /* 오늘 이미 사용한 횟수 */ 0,
                        /* 이번 달 이미 사용한 횟수 */ 0,
                        /* 대상 가맹점 여부 */ true,
                        /* 지정 결제 채널 여부 */ true));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("대형마트: 1회 인정금액 50,000원 초과분은 혜택에서 제외한다")
    void capsBenefit008BenefitBaseAmount() {
        // 1회 인정금액과 정확히 같은 결제의 혜택을 계산한다.
        BenefitCalculationResult atLimit = fixture.calculator.calculate(
                benefit008Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "50000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "OFFLINE_SHOPPING"));

        // 인정금액보다 1원 큰 결제로 초과분이 계산 기준에서 제외되는지 확인한다.
        BenefitCalculationResult overLimit = fixture.calculator.calculate(
                benefit008Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "50001",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "OFFLINE_SHOPPING"));

        // 두 결제의 혜택이 같으면 1회 인정금액 상한이 정상 적용된 것이다.
        fixture.assertApplied(atLimit);
        fixture.assertApplied(overLimit);
        fixture.assertBigDecimalEquals(atLimit.rawRewardValue(), overLimit.rawRewardValue());
    }

    @Test
    @DisplayName("대형마트: 월 한도가 1원 남았으면 1원만 적용한다")
    void appliesOnlyRemainingMonthlyLimitForBenefit008() {
        // 월 한도에서 이미 사용한 혜택을 빼고 1원만 남은 상태로 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit008Rule(/* 이번 달에 이미 사용한 혜택 */ "2999"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "OFFLINE_SHOPPING"));

        // 계산 혜택이 더 크더라도 남은 1원만 적용되고 월 한도는 0원이 되어야 한다.
        fixture.assertApplied(result);
        fixture.assertBigDecimalEquals("1", result.appliedRewardValue());
        fixture.assertBigDecimalEquals("0", result.remainingLimitValue());
    }

    @Test
    @DisplayName("대형마트: 월 한도를 모두 사용했으면 적용하지 않는다")
    void rejectsBenefit008WhenMonthlyLimitIsExhausted() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit008Rule(/* 이번 달에 이미 사용한 혜택 */ "3000"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "OFFLINE_SHOPPING"));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.MONTHLY_LIMIT_EXHAUSTED);
    }

    @Test
    @DisplayName("대형마트: 일 1회 한도의 마지막 허용 거래는 적용한다")
    void appliesBenefit008WhenDailyUsageIsLastAllowed() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit008Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "OFFLINE_SHOPPING"));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("대형마트: 일 1회 사용 후에는 적용하지 않는다")
    void rejectsBenefit008WhenDailyUsageLimitIsExhausted() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit008Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "OFFLINE_SHOPPING",
                        /* 신규 발급 실적 유예 여부 */ false,
                        /* 오늘 이미 사용한 횟수 */ 1,
                        /* 이번 달 이미 사용한 횟수 */ 0,
                        /* 대상 가맹점 여부 */ true,
                        /* 지정 결제 채널 여부 */ true));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.FREQUENCY_LIMIT_EXHAUSTED);
    }

    @Test
    @DisplayName("대형마트: 제외 가맹점에서는 적용하지 않는다")
    void rejectsBenefit008WhenMerchantIsNotEligible() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit008Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "OFFLINE_SHOPPING",
                        /* 신규 발급 실적 유예 여부 */ false,
                        /* 오늘 이미 사용한 횟수 */ 0,
                        /* 이번 달 이미 사용한 횟수 */ 0,
                        /* 대상 가맹점 여부 */ false,
                        /* 지정 결제 채널 여부 */ true));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.MERCHANT_NOT_ELIGIBLE);
    }

    @Test
    @DisplayName("대형마트: 정률 혜택의 원 미만 금액을 절사한다")
    void floorsBenefit008FractionalReward() {
        // 비율 계산 결과에 원 미만 소수가 생기는 결제금액으로 절사 정책을 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit008Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "10001",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "OFFLINE_SHOPPING"));

        // 카드 혜택은 원 미만 금액을 올림하지 않고 버린 결과와 같아야 한다.
        fixture.assertApplied(result);
        fixture.assertBigDecimalEquals("1000", result.rawRewardValue());
    }

    @Test
    @DisplayName("주유소: 사용 단위당 60원 할인 정상 적용")
    void appliesBenefit009() {
        // 테스트에 사용한 계산 규칙이 카드고릴라의 해당 혜택 상세에서 만들어졌는지 확인한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "13",
                /* 카드 내 혜택 순번 */ 9,
                /* benefit_title */ "주유소",
                /* 계산 지원 상태 */ "DIRECT_OFFLINE_CALCULABLE",
                /* 분류 사유 */ "직접 카드 결제 혜택 산식 계산 가능");

        // 카드 혜택 룰과 현재 결제 상황을 조합해 예상 혜택을 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit009Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "40",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "FUEL_CAR"));

        // 월 한도 반영 전 혜택, 실제 적용 혜택, 계산 후 남은 월 한도를 차례로 검증한다.
        fixture.assertApplied(
                result,
                /* 월 한도 반영 전 혜택 */ "2400",
                /* 실제 적용 혜택 */ "2400",
                /* 남은 월 한도 */ "600");
    }

    @Test
    @DisplayName("주유소: 카테고리가 일치하지 않으면 적용하지 않는다")
    void rejectsBenefit009WhenCategoryIsNotMatched() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit009Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "40",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ NOT_MATCHED_CATEGORY));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.CATEGORY_NOT_MATCHED);
    }

    @Test
    @DisplayName("주유소: 전월 실적 300,000원보다 1원 적으면 적용하지 않는다")
    void rejectsBenefit009WhenPerformanceIsOneWonBelowRequirement() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit009Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "40",
                        /* 전월 실적 */ "299999",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "FUEL_CAR"));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.PERFORMANCE_NOT_MET);
    }

    @Test
    @DisplayName("주유소: 전월 실적 300,000원부터 적용한다")
    void appliesBenefit009WhenPerformanceEqualsRequirement() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit009Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "40",
                        /* 전월 실적 */ "300000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "FUEL_CAR"));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("주유소: 신규 발급 유예기간에는 전월 실적 0원도 적용한다")
    void appliesBenefit009WhenNewMemberGracePeriodIsActive() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit009Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "40",
                        /* 전월 실적 */ "0",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "FUEL_CAR",
                        /* 신규 발급 실적 유예 여부 */ true,
                        /* 오늘 이미 사용한 횟수 */ 0,
                        /* 이번 달 이미 사용한 횟수 */ 0,
                        /* 대상 가맹점 여부 */ true,
                        /* 지정 결제 채널 여부 */ true));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("주유소: 월 한도가 1원 남았으면 1원만 적용한다")
    void appliesOnlyRemainingMonthlyLimitForBenefit009() {
        // 월 한도에서 이미 사용한 혜택을 빼고 1원만 남은 상태로 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit009Rule(/* 이번 달에 이미 사용한 혜택 */ "2999"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "40",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "FUEL_CAR"));

        // 계산 혜택이 더 크더라도 남은 1원만 적용되고 월 한도는 0원이 되어야 한다.
        fixture.assertApplied(result);
        fixture.assertBigDecimalEquals("1", result.appliedRewardValue());
        fixture.assertBigDecimalEquals("0", result.remainingLimitValue());
    }

    @Test
    @DisplayName("주유소: 월 한도를 모두 사용했으면 적용하지 않는다")
    void rejectsBenefit009WhenMonthlyLimitIsExhausted() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit009Rule(/* 이번 달에 이미 사용한 혜택 */ "3000"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "40",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "FUEL_CAR"));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.MONTHLY_LIMIT_EXHAUSTED);
    }

    @Test
    @DisplayName("주유소: 일 1회 한도의 마지막 허용 거래는 적용한다")
    void appliesBenefit009WhenDailyUsageIsLastAllowed() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit009Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "40",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "FUEL_CAR"));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("주유소: 일 1회 사용 후에는 적용하지 않는다")
    void rejectsBenefit009WhenDailyUsageLimitIsExhausted() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit009Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "40",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "FUEL_CAR",
                        /* 신규 발급 실적 유예 여부 */ false,
                        /* 오늘 이미 사용한 횟수 */ 1,
                        /* 이번 달 이미 사용한 횟수 */ 0,
                        /* 대상 가맹점 여부 */ true,
                        /* 지정 결제 채널 여부 */ true));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.FREQUENCY_LIMIT_EXHAUSTED);
    }

    @Test
    @DisplayName("주유소: 사용량이 0이면 혜택도 0으로 계산한다")
    void calculatesZeroBenefit009ForZeroUsage() {
        // 리터·횟수처럼 사용량을 기준으로 계산하는 혜택에 사용량 0을 입력한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit009Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "FUEL_CAR"));

        // 결제 승인 자체는 유효하지만 사용량이 없으므로 계산 혜택은 0이어야 한다.
        fixture.assertApplied(result);
        fixture.assertBigDecimalEquals("0", result.rawRewardValue());
    }

    @Test
    @DisplayName("온라인쇼핑: 온라인·간편결제 범위 제외 - 온라인·앱·배달·간편결제·자동납부 전용 혜택")
    void classifiesBenefit010() {
        // 계산하지 않는 상세도 누락하지 않고 원본 분류 상태와 제외·검토 사유를 고정한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "13",
                /* 카드 내 혜택 순번 */ 10,
                /* benefit_title */ "온라인쇼핑",
                /* 계산 지원 상태 */ "ONLINE_OR_INDIRECT_EXCLUDED",
                /* 분류 사유 */ "온라인·앱·배달·간편결제·자동납부 전용 혜택");
    }

    @Test
    @DisplayName("유의사항: 계산 규칙이 아닌 상세 정보 - 유의사항·발급·연회비 등 계산 산식이 아닌 상세 정보")
    void classifiesBenefit011() {
        // 계산하지 않는 상세도 누락하지 않고 원본 분류 상태와 제외·검토 사유를 고정한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "13",
                /* 카드 내 혜택 순번 */ 11,
                /* benefit_title */ "유의사항",
                /* 계산 지원 상태 */ "NON_RULE_DETAIL",
                /* 분류 사유 */ "유의사항·발급·연회비 등 계산 산식이 아닌 상세 정보");
    }

    /**
     * benefit_title=편의점의 계산 조건을 만든다.
     *
     * @param usedMonthlyValue 이번 달에 이미 적용받은 혜택 누적값
     * @return 금액·실적·한도·횟수·적용 조건이 구조화된 혜택 룰
     */
    private BenefitRule benefit002Rule(String usedMonthlyValue) {
        return fixture.rule(
                /* 룰 식별자 */ "card-13-benefit-2",
                /* 할인·캐시백·포인트·마일리지 구분 */ BenefitType.DISCOUNT,
                /* 정률·정액·결제 단위·사용량 단위 구분 */ BenefitBasis.RATE,
                /* 혜택 결과 단위 */ RewardUnit.KRW,
                /* 정률 계산 비율 */ "0.1",
                /* 정액 또는 단위당 혜택값 */ "0",
                /* 단위 적립 기준 결제금액 */ "0",
                /* 1회 혜택 인정금액 상한 */ "10000",
                /* 혜택 적용 최소 결제금액 */ "0",
                /* 필요한 전월 실적 */ "300000",
                /* 월 혜택 한도 */ "10000",
                /* 이번 달에 이미 사용한 혜택 */ usedMonthlyValue,
                /* 시간·요일 조건 */ BenefitPromotionCondition.NONE,
                /* 적용 MOCA 카테고리 */ "CONVENIENCE_STORE",
                /* 일 사용 횟수 한도 */ 1,
                /* 월 사용 횟수 한도 */ 5,
                /* 대상 가맹점 확인 필요 여부 */ false,
                /* 지정 결제 채널 확인 필요 여부 */ false);
    }

    /**
     * benefit_title=병원/약국의 계산 조건을 만든다.
     *
     * @param usedMonthlyValue 이번 달에 이미 적용받은 혜택 누적값
     * @return 금액·실적·한도·횟수·적용 조건이 구조화된 혜택 룰
     */
    private BenefitRule benefit003Rule(String usedMonthlyValue) {
        return fixture.rule(
                /* 룰 식별자 */ "card-13-benefit-3",
                /* 할인·캐시백·포인트·마일리지 구분 */ BenefitType.DISCOUNT,
                /* 정률·정액·결제 단위·사용량 단위 구분 */ BenefitBasis.RATE,
                /* 혜택 결과 단위 */ RewardUnit.KRW,
                /* 정률 계산 비율 */ "0.1",
                /* 정액 또는 단위당 혜택값 */ "0",
                /* 단위 적립 기준 결제금액 */ "0",
                /* 1회 혜택 인정금액 상한 */ "10000",
                /* 혜택 적용 최소 결제금액 */ "0",
                /* 필요한 전월 실적 */ "300000",
                /* 월 혜택 한도 */ "10000",
                /* 이번 달에 이미 사용한 혜택 */ usedMonthlyValue,
                /* 시간·요일 조건 */ BenefitPromotionCondition.NONE,
                /* 적용 MOCA 카테고리 */ "MEDICAL_BEAUTY",
                /* 일 사용 횟수 한도 */ 1,
                /* 월 사용 횟수 한도 */ 5,
                /* 대상 가맹점 확인 필요 여부 */ false,
                /* 지정 결제 채널 확인 필요 여부 */ false);
    }

    /**
     * benefit_title=생활의 계산 조건을 만든다.
     *
     * @param usedMonthlyValue 이번 달에 이미 적용받은 혜택 누적값
     * @return 금액·실적·한도·횟수·적용 조건이 구조화된 혜택 룰
     */
    private BenefitRule benefit004Rule(String usedMonthlyValue) {
        return fixture.rule(
                /* 룰 식별자 */ "card-13-benefit-4",
                /* 할인·캐시백·포인트·마일리지 구분 */ BenefitType.DISCOUNT,
                /* 정률·정액·결제 단위·사용량 단위 구분 */ BenefitBasis.RATE,
                /* 혜택 결과 단위 */ RewardUnit.KRW,
                /* 정률 계산 비율 */ "0.1",
                /* 정액 또는 단위당 혜택값 */ "0",
                /* 단위 적립 기준 결제금액 */ "0",
                /* 1회 혜택 인정금액 상한 */ "10000",
                /* 혜택 적용 최소 결제금액 */ "0",
                /* 필요한 전월 실적 */ "300000",
                /* 월 혜택 한도 */ "10000",
                /* 이번 달에 이미 사용한 혜택 */ usedMonthlyValue,
                /* 시간·요일 조건 */ BenefitPromotionCondition.NONE,
                /* 적용 MOCA 카테고리 */ "LAUNDRY",
                /* 일 사용 횟수 한도 */ 1,
                /* 월 사용 횟수 한도 */ 5,
                /* 대상 가맹점 확인 필요 여부 */ false,
                /* 지정 결제 채널 확인 필요 여부 */ false);
    }

    /**
     * benefit_title=택시의 계산 조건을 만든다.
     *
     * @param usedMonthlyValue 이번 달에 이미 적용받은 혜택 누적값
     * @return 금액·실적·한도·횟수·적용 조건이 구조화된 혜택 룰
     */
    private BenefitRule benefit006Rule(String usedMonthlyValue) {
        return fixture.rule(
                /* 룰 식별자 */ "card-13-benefit-6",
                /* 할인·캐시백·포인트·마일리지 구분 */ BenefitType.DISCOUNT,
                /* 정률·정액·결제 단위·사용량 단위 구분 */ BenefitBasis.RATE,
                /* 혜택 결과 단위 */ RewardUnit.KRW,
                /* 정률 계산 비율 */ "0.1",
                /* 정액 또는 단위당 혜택값 */ "0",
                /* 단위 적립 기준 결제금액 */ "0",
                /* 1회 혜택 인정금액 상한 */ "10000",
                /* 혜택 적용 최소 결제금액 */ "0",
                /* 필요한 전월 실적 */ "300000",
                /* 월 혜택 한도 */ "10000",
                /* 이번 달에 이미 사용한 혜택 */ usedMonthlyValue,
                /* 시간·요일 조건 */ BenefitPromotionCondition.NONE,
                /* 적용 MOCA 카테고리 */ "TAXI_MOBILITY",
                /* 일 사용 횟수 한도 */ 1,
                /* 월 사용 횟수 한도 */ 10,
                /* 대상 가맹점 확인 필요 여부 */ false,
                /* 지정 결제 채널 확인 필요 여부 */ false);
    }

    /**
     * benefit_title=푸드의 계산 조건을 만든다.
     *
     * @param usedMonthlyValue 이번 달에 이미 적용받은 혜택 누적값
     * @return 금액·실적·한도·횟수·적용 조건이 구조화된 혜택 룰
     */
    private BenefitRule benefit007Rule(String usedMonthlyValue) {
        return fixture.rule(
                /* 룰 식별자 */ "card-13-benefit-7",
                /* 할인·캐시백·포인트·마일리지 구분 */ BenefitType.DISCOUNT,
                /* 정률·정액·결제 단위·사용량 단위 구분 */ BenefitBasis.RATE,
                /* 혜택 결과 단위 */ RewardUnit.KRW,
                /* 정률 계산 비율 */ "0.1",
                /* 정액 또는 단위당 혜택값 */ "0",
                /* 단위 적립 기준 결제금액 */ "0",
                /* 1회 혜택 인정금액 상한 */ "10000",
                /* 혜택 적용 최소 결제금액 */ "0",
                /* 필요한 전월 실적 */ "300000",
                /* 월 혜택 한도 */ "10000",
                /* 이번 달에 이미 사용한 혜택 */ usedMonthlyValue,
                /* 시간·요일 조건 */ BenefitPromotionCondition.NONE,
                /* 적용 MOCA 카테고리 */ "FOOD_DINING",
                /* 일 사용 횟수 한도 */ 1,
                /* 월 사용 횟수 한도 */ 10,
                /* 대상 가맹점 확인 필요 여부 */ false,
                /* 지정 결제 채널 확인 필요 여부 */ false);
    }

    /**
     * benefit_title=대형마트의 계산 조건을 만든다.
     *
     * @param usedMonthlyValue 이번 달에 이미 적용받은 혜택 누적값
     * @return 금액·실적·한도·횟수·적용 조건이 구조화된 혜택 룰
     */
    private BenefitRule benefit008Rule(String usedMonthlyValue) {
        return fixture.rule(
                /* 룰 식별자 */ "card-13-benefit-8",
                /* 할인·캐시백·포인트·마일리지 구분 */ BenefitType.DISCOUNT,
                /* 정률·정액·결제 단위·사용량 단위 구분 */ BenefitBasis.RATE,
                /* 혜택 결과 단위 */ RewardUnit.KRW,
                /* 정률 계산 비율 */ "0.1",
                /* 정액 또는 단위당 혜택값 */ "0",
                /* 단위 적립 기준 결제금액 */ "0",
                /* 1회 혜택 인정금액 상한 */ "50000",
                /* 혜택 적용 최소 결제금액 */ "0",
                /* 필요한 전월 실적 */ "300000",
                /* 월 혜택 한도 */ "3000",
                /* 이번 달에 이미 사용한 혜택 */ usedMonthlyValue,
                /* 시간·요일 조건 */ BenefitPromotionCondition.NONE,
                /* 적용 MOCA 카테고리 */ "OFFLINE_SHOPPING",
                /* 일 사용 횟수 한도 */ 1,
                /* 월 사용 횟수 한도 */ 0,
                /* 대상 가맹점 확인 필요 여부 */ true,
                /* 지정 결제 채널 확인 필요 여부 */ false);
    }

    /**
     * benefit_title=주유소의 계산 조건을 만든다.
     *
     * @param usedMonthlyValue 이번 달에 이미 적용받은 혜택 누적값
     * @return 금액·실적·한도·횟수·적용 조건이 구조화된 혜택 룰
     */
    private BenefitRule benefit009Rule(String usedMonthlyValue) {
        return fixture.rule(
                /* 룰 식별자 */ "card-13-benefit-9",
                /* 할인·캐시백·포인트·마일리지 구분 */ BenefitType.DISCOUNT,
                /* 정률·정액·결제 단위·사용량 단위 구분 */ BenefitBasis.PER_USAGE_UNIT,
                /* 혜택 결과 단위 */ RewardUnit.KRW,
                /* 정률 계산 비율 */ "0",
                /* 정액 또는 단위당 혜택값 */ "60",
                /* 단위 적립 기준 결제금액 */ "0",
                /* 1회 혜택 인정금액 상한 */ "0",
                /* 혜택 적용 최소 결제금액 */ "0",
                /* 필요한 전월 실적 */ "300000",
                /* 월 혜택 한도 */ "3000",
                /* 이번 달에 이미 사용한 혜택 */ usedMonthlyValue,
                /* 시간·요일 조건 */ BenefitPromotionCondition.NONE,
                /* 적용 MOCA 카테고리 */ "FUEL_CAR",
                /* 일 사용 횟수 한도 */ 1,
                /* 월 사용 횟수 한도 */ 0,
                /* 대상 가맹점 확인 필요 여부 */ false,
                /* 지정 결제 채널 확인 필요 여부 */ false);
    }
}
