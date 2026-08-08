package com.moca.mocabe.domain.benefit.calculation.card.generated.check;

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


@DisplayName("체크 43위 신한카드 Hey Young 체크")
class CheckRank043Card618BenefitTest {

    private static final String NOT_MATCHED_CATEGORY = "__NOT_MATCHED__";

    private final CardBenefitTestFixture fixture = new CardBenefitTestFixture();

    @Test
    @DisplayName("선택형: 정보성 혜택 - 바우처·수수료·무이자할부 등 결제금액 역산 대상이 아닌 혜택")
    void classifiesBenefit001() {
        // 계산하지 않는 상세도 누락하지 않고 원본 분류 상태와 제외·검토 사유를 고정한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "618",
                /* 카드 내 혜택 순번 */ 1,
                /* benefit_title */ "선택형",
                /* 계산 지원 상태 */ "INFORMATION_ONLY",
                /* 분류 사유 */ "바우처·수수료·무이자할부 등 결제금액 역산 대상이 아닌 혜택");
    }

    @Test
    @DisplayName("대중교통: 5% 캐시백 정상 적용")
    void appliesBenefit002() {
        // 테스트에 사용한 계산 규칙이 카드고릴라의 해당 혜택 상세에서 만들어졌는지 확인한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "618",
                /* 카드 내 혜택 순번 */ 2,
                /* benefit_title */ "대중교통",
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
                        /* MOCA 가맹점 카테고리 */ "TAXI_MOBILITY"));

        // 월 한도 반영 전 혜택, 실제 적용 혜택, 계산 후 남은 월 한도를 차례로 검증한다.
        fixture.assertApplied(
                result,
                /* 월 한도 반영 전 혜택 */ "5000",
                /* 실제 적용 혜택 */ "5000",
                /* 남은 월 한도 */ "0");
    }

    @Test
    @DisplayName("대중교통: 카테고리가 일치하지 않으면 적용하지 않는다")
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
    @DisplayName("대중교통: 전월 실적 200,000원보다 1원 적으면 적용하지 않는다")
    void rejectsBenefit002WhenPerformanceIsOneWonBelowRequirement() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit002Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "199999",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "TAXI_MOBILITY"));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.PERFORMANCE_NOT_MET);
    }

    @Test
    @DisplayName("대중교통: 전월 실적 200,000원부터 적용한다")
    void appliesBenefit002WhenPerformanceEqualsRequirement() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit002Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "200000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "TAXI_MOBILITY"));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("대중교통: 정률 혜택의 원 미만 금액을 절사한다")
    void floorsBenefit002FractionalReward() {
        // 비율 계산 결과에 원 미만 소수가 생기는 결제금액으로 절사 정책을 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit002Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "10001",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "TAXI_MOBILITY"));

        // 카드 혜택은 원 미만 금액을 올림하지 않고 버린 결과와 같아야 한다.
        fixture.assertApplied(result);
        fixture.assertBigDecimalEquals("500", result.rawRewardValue());
    }

    @Test
    @DisplayName("통신: 온라인·간편결제 범위 제외 - 온라인·앱·배달·간편결제·자동납부 전용 혜택")
    void classifiesBenefit003() {
        // 계산하지 않는 상세도 누락하지 않고 원본 분류 상태와 제외·검토 사유를 고정한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "618",
                /* 카드 내 혜택 순번 */ 3,
                /* benefit_title */ "통신",
                /* 계산 지원 상태 */ "ONLINE_OR_INDIRECT_EXCLUDED",
                /* 분류 사유 */ "온라인·앱·배달·간편결제·자동납부 전용 혜택");
    }

    @Test
    @DisplayName("영화: 5000원 정액 캐시백 정상 적용")
    void appliesBenefit004() {
        // 테스트에 사용한 계산 규칙이 카드고릴라의 해당 혜택 상세에서 만들어졌는지 확인한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "618",
                /* 카드 내 혜택 순번 */ 4,
                /* benefit_title */ "영화",
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
                        /* MOCA 가맹점 카테고리 */ "MOVIE_CULTURE"));

        // 월 한도 반영 전 혜택, 실제 적용 혜택, 계산 후 남은 월 한도를 차례로 검증한다.
        fixture.assertApplied(
                result,
                /* 월 한도 반영 전 혜택 */ "5000",
                /* 실제 적용 혜택 */ "5000",
                /* 남은 월 한도 */ "0");
    }

    @Test
    @DisplayName("영화: 카테고리가 일치하지 않으면 적용하지 않는다")
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
    @DisplayName("영화: 최소 결제금액 20,000원보다 1원 적으면 적용하지 않는다")
    void rejectsBenefit004WhenPaymentIsOneWonBelowMinimum() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit004Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "19999",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "MOVIE_CULTURE"));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.MIN_PAYMENT_NOT_MET);
    }

    @Test
    @DisplayName("영화: 최소 결제금액 20,000원부터 적용한다")
    void appliesBenefit004WhenPaymentEqualsMinimum() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit004Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "20000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "MOVIE_CULTURE"));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("영화: 전월 실적 200,000원보다 1원 적으면 적용하지 않는다")
    void rejectsBenefit004WhenPerformanceIsOneWonBelowRequirement() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit004Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "199999",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "MOVIE_CULTURE"));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.PERFORMANCE_NOT_MET);
    }

    @Test
    @DisplayName("영화: 전월 실적 200,000원부터 적용한다")
    void appliesBenefit004WhenPerformanceEqualsRequirement() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit004Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "200000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "MOVIE_CULTURE"));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("영화: 월 1회 한도의 마지막 허용 거래는 적용한다")
    void appliesBenefit004WhenMonthlyUsageIsLastAllowed() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit004Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "MOVIE_CULTURE"));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("영화: 월 1회 사용 후에는 적용하지 않는다")
    void rejectsBenefit004WhenMonthlyUsageLimitIsExhausted() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit004Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "MOVIE_CULTURE",
                        /* 신규 발급 실적 유예 여부 */ false,
                        /* 오늘 이미 사용한 횟수 */ 0,
                        /* 이번 달 이미 사용한 횟수 */ 1,
                        /* 대상 가맹점 여부 */ true,
                        /* 지정 결제 채널 여부 */ true));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.FREQUENCY_LIMIT_EXHAUSTED);
    }

    @Test
    @DisplayName("영화: 제외 가맹점에서는 적용하지 않는다")
    void rejectsBenefit004WhenMerchantIsNotEligible() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit004Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "MOVIE_CULTURE",
                        /* 신규 발급 실적 유예 여부 */ false,
                        /* 오늘 이미 사용한 횟수 */ 0,
                        /* 이번 달 이미 사용한 횟수 */ 0,
                        /* 대상 가맹점 여부 */ false,
                        /* 지정 결제 채널 여부 */ true));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.MERCHANT_NOT_ELIGIBLE);
    }

    @Test
    @DisplayName("편의점: 1000원 정액 캐시백 정상 적용")
    void appliesBenefit005() {
        // 테스트에 사용한 계산 규칙이 카드고릴라의 해당 혜택 상세에서 만들어졌는지 확인한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "618",
                /* 카드 내 혜택 순번 */ 5,
                /* benefit_title */ "편의점",
                /* 계산 지원 상태 */ "DIRECT_OFFLINE_CALCULABLE",
                /* 분류 사유 */ "직접 카드 결제 혜택 산식 계산 가능");

        // 카드 혜택 룰과 현재 결제 상황을 조합해 예상 혜택을 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit005Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
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
                /* 남은 월 한도 */ "0");
    }

    @Test
    @DisplayName("편의점: 카테고리가 일치하지 않으면 적용하지 않는다")
    void rejectsBenefit005WhenCategoryIsNotMatched() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit005Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
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
    @DisplayName("편의점: 최소 결제금액 10,000원보다 1원 적으면 적용하지 않는다")
    void rejectsBenefit005WhenPaymentIsOneWonBelowMinimum() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit005Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "9999",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "CONVENIENCE_STORE"));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.MIN_PAYMENT_NOT_MET);
    }

    @Test
    @DisplayName("편의점: 최소 결제금액 10,000원부터 적용한다")
    void appliesBenefit005WhenPaymentEqualsMinimum() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit005Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "10000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "CONVENIENCE_STORE"));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("편의점: 전월 실적 200,000원보다 1원 적으면 적용하지 않는다")
    void rejectsBenefit005WhenPerformanceIsOneWonBelowRequirement() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit005Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "199999",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "CONVENIENCE_STORE"));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.PERFORMANCE_NOT_MET);
    }

    @Test
    @DisplayName("편의점: 전월 실적 200,000원부터 적용한다")
    void appliesBenefit005WhenPerformanceEqualsRequirement() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit005Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "200000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "CONVENIENCE_STORE"));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("편의점: 일 1회 한도의 마지막 허용 거래는 적용한다")
    void appliesBenefit005WhenDailyUsageIsLastAllowed() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit005Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
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
    void rejectsBenefit005WhenDailyUsageLimitIsExhausted() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit005Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
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
    void appliesBenefit005WhenMonthlyUsageIsLastAllowed() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit005Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
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
    void rejectsBenefit005WhenMonthlyUsageLimitIsExhausted() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit005Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
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
    @DisplayName("편의점: 지정 결제 채널이 아니면 적용하지 않는다")
    void rejectsBenefit005WhenPaymentChannelIsNotEligible() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit005Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "CONVENIENCE_STORE",
                        /* 신규 발급 실적 유예 여부 */ false,
                        /* 오늘 이미 사용한 횟수 */ 0,
                        /* 이번 달 이미 사용한 횟수 */ 0,
                        /* 대상 가맹점 여부 */ true,
                        /* 지정 결제 채널 여부 */ false));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.PAYMENT_CHANNEL_NOT_ELIGIBLE);
    }

    @Test
    @DisplayName("카페: 1000원 정액 캐시백 정상 적용")
    void appliesBenefit006() {
        // 테스트에 사용한 계산 규칙이 카드고릴라의 해당 혜택 상세에서 만들어졌는지 확인한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "618",
                /* 카드 내 혜택 순번 */ 6,
                /* benefit_title */ "카페",
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
                        /* MOCA 가맹점 카테고리 */ "FOOD_DINING"));

        // 월 한도 반영 전 혜택, 실제 적용 혜택, 계산 후 남은 월 한도를 차례로 검증한다.
        fixture.assertApplied(
                result,
                /* 월 한도 반영 전 혜택 */ "1000",
                /* 실제 적용 혜택 */ "1000",
                /* 남은 월 한도 */ "0");
    }

    @Test
    @DisplayName("카페: 카테고리가 일치하지 않으면 적용하지 않는다")
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
    @DisplayName("카페: 최소 결제금액 10,000원보다 1원 적으면 적용하지 않는다")
    void rejectsBenefit006WhenPaymentIsOneWonBelowMinimum() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit006Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "9999",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "FOOD_DINING"));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.MIN_PAYMENT_NOT_MET);
    }

    @Test
    @DisplayName("카페: 최소 결제금액 10,000원부터 적용한다")
    void appliesBenefit006WhenPaymentEqualsMinimum() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit006Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "10000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "FOOD_DINING"));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("카페: 전월 실적 200,000원보다 1원 적으면 적용하지 않는다")
    void rejectsBenefit006WhenPerformanceIsOneWonBelowRequirement() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit006Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "199999",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "FOOD_DINING"));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.PERFORMANCE_NOT_MET);
    }

    @Test
    @DisplayName("카페: 전월 실적 200,000원부터 적용한다")
    void appliesBenefit006WhenPerformanceEqualsRequirement() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit006Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "200000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "FOOD_DINING"));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("카페: 일 1회 한도의 마지막 허용 거래는 적용한다")
    void appliesBenefit006WhenDailyUsageIsLastAllowed() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit006Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
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
    @DisplayName("카페: 일 1회 사용 후에는 적용하지 않는다")
    void rejectsBenefit006WhenDailyUsageLimitIsExhausted() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit006Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
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
    @DisplayName("카페: 월 5회 한도의 마지막 허용 거래는 적용한다")
    void appliesBenefit006WhenMonthlyUsageIsLastAllowed() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit006Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "FOOD_DINING",
                        /* 신규 발급 실적 유예 여부 */ false,
                        /* 오늘 이미 사용한 횟수 */ 0,
                        /* 이번 달 이미 사용한 횟수 */ 4,
                        /* 대상 가맹점 여부 */ true,
                        /* 지정 결제 채널 여부 */ true));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("카페: 월 5회 사용 후에는 적용하지 않는다")
    void rejectsBenefit006WhenMonthlyUsageLimitIsExhausted() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit006Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "FOOD_DINING",
                        /* 신규 발급 실적 유예 여부 */ false,
                        /* 오늘 이미 사용한 횟수 */ 0,
                        /* 이번 달 이미 사용한 횟수 */ 5,
                        /* 대상 가맹점 여부 */ true,
                        /* 지정 결제 채널 여부 */ true));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.FREQUENCY_LIMIT_EXHAUSTED);
    }

    @Test
    @DisplayName("카페: 지정 결제 채널이 아니면 적용하지 않는다")
    void rejectsBenefit006WhenPaymentChannelIsNotEligible() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit006Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "FOOD_DINING",
                        /* 신규 발급 실적 유예 여부 */ false,
                        /* 오늘 이미 사용한 횟수 */ 0,
                        /* 이번 달 이미 사용한 횟수 */ 0,
                        /* 대상 가맹점 여부 */ true,
                        /* 지정 결제 채널 여부 */ false));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.PAYMENT_CHANNEL_NOT_ELIGIBLE);
    }

    @Test
    @DisplayName("패스트푸드: 1000원 정액 캐시백 정상 적용")
    void appliesBenefit007() {
        // 테스트에 사용한 계산 규칙이 카드고릴라의 해당 혜택 상세에서 만들어졌는지 확인한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "618",
                /* 카드 내 혜택 순번 */ 7,
                /* benefit_title */ "패스트푸드",
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
                /* 남은 월 한도 */ "0");
    }

    @Test
    @DisplayName("패스트푸드: 카테고리가 일치하지 않으면 적용하지 않는다")
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
    @DisplayName("패스트푸드: 최소 결제금액 10,000원보다 1원 적으면 적용하지 않는다")
    void rejectsBenefit007WhenPaymentIsOneWonBelowMinimum() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit007Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "9999",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "FOOD_DINING"));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.MIN_PAYMENT_NOT_MET);
    }

    @Test
    @DisplayName("패스트푸드: 최소 결제금액 10,000원부터 적용한다")
    void appliesBenefit007WhenPaymentEqualsMinimum() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit007Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "10000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "FOOD_DINING"));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("패스트푸드: 전월 실적 200,000원보다 1원 적으면 적용하지 않는다")
    void rejectsBenefit007WhenPerformanceIsOneWonBelowRequirement() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit007Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "199999",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "FOOD_DINING"));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.PERFORMANCE_NOT_MET);
    }

    @Test
    @DisplayName("패스트푸드: 전월 실적 200,000원부터 적용한다")
    void appliesBenefit007WhenPerformanceEqualsRequirement() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit007Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "200000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "FOOD_DINING"));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("패스트푸드: 일 1회 한도의 마지막 허용 거래는 적용한다")
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
    @DisplayName("패스트푸드: 일 1회 사용 후에는 적용하지 않는다")
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
    @DisplayName("패스트푸드: 월 5회 한도의 마지막 허용 거래는 적용한다")
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
                        /* 이번 달 이미 사용한 횟수 */ 4,
                        /* 대상 가맹점 여부 */ true,
                        /* 지정 결제 채널 여부 */ true));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("패스트푸드: 월 5회 사용 후에는 적용하지 않는다")
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
                        /* 이번 달 이미 사용한 횟수 */ 5,
                        /* 대상 가맹점 여부 */ true,
                        /* 지정 결제 채널 여부 */ true));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.FREQUENCY_LIMIT_EXHAUSTED);
    }

    @Test
    @DisplayName("패스트푸드: 지정 결제 채널이 아니면 적용하지 않는다")
    void rejectsBenefit007WhenPaymentChannelIsNotEligible() {
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
                        /* 이번 달 이미 사용한 횟수 */ 0,
                        /* 대상 가맹점 여부 */ true,
                        /* 지정 결제 채널 여부 */ false));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.PAYMENT_CHANNEL_NOT_ELIGIBLE);
    }

    @Test
    @DisplayName("배달앱: 온라인·간편결제 범위 제외 - 온라인·앱·배달·간편결제·자동납부 전용 혜택")
    void classifiesBenefit008() {
        // 계산하지 않는 상세도 누락하지 않고 원본 분류 상태와 제외·검토 사유를 고정한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "618",
                /* 카드 내 혜택 순번 */ 8,
                /* benefit_title */ "배달앱",
                /* 계산 지원 상태 */ "ONLINE_OR_INDIRECT_EXCLUDED",
                /* 분류 사유 */ "온라인·앱·배달·간편결제·자동납부 전용 혜택");
    }

    @Test
    @DisplayName("생활: 1000원 정액 캐시백 정상 적용")
    void appliesBenefit009() {
        // 테스트에 사용한 계산 규칙이 카드고릴라의 해당 혜택 상세에서 만들어졌는지 확인한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "618",
                /* 카드 내 혜택 순번 */ 9,
                /* benefit_title */ "생활",
                /* 계산 지원 상태 */ "DIRECT_OFFLINE_CALCULABLE",
                /* 분류 사유 */ "직접 카드 결제 혜택 산식 계산 가능");

        // 카드 혜택 룰과 현재 결제 상황을 조합해 예상 혜택을 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit009Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
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
                /* 남은 월 한도 */ "0");
    }

    @Test
    @DisplayName("생활: 카테고리가 일치하지 않으면 적용하지 않는다")
    void rejectsBenefit009WhenCategoryIsNotMatched() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit009Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
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
    @DisplayName("생활: 최소 결제금액 10,000원보다 1원 적으면 적용하지 않는다")
    void rejectsBenefit009WhenPaymentIsOneWonBelowMinimum() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit009Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "9999",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "MEDICAL_BEAUTY"));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.MIN_PAYMENT_NOT_MET);
    }

    @Test
    @DisplayName("생활: 최소 결제금액 10,000원부터 적용한다")
    void appliesBenefit009WhenPaymentEqualsMinimum() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit009Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "10000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "MEDICAL_BEAUTY"));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("생활: 전월 실적 200,000원보다 1원 적으면 적용하지 않는다")
    void rejectsBenefit009WhenPerformanceIsOneWonBelowRequirement() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit009Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "199999",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "MEDICAL_BEAUTY"));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.PERFORMANCE_NOT_MET);
    }

    @Test
    @DisplayName("생활: 전월 실적 200,000원부터 적용한다")
    void appliesBenefit009WhenPerformanceEqualsRequirement() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit009Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "200000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "MEDICAL_BEAUTY"));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("생활: 일 1회 한도의 마지막 허용 거래는 적용한다")
    void appliesBenefit009WhenDailyUsageIsLastAllowed() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit009Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
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
    @DisplayName("생활: 일 1회 사용 후에는 적용하지 않는다")
    void rejectsBenefit009WhenDailyUsageLimitIsExhausted() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit009Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
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
    @DisplayName("생활: 월 5회 한도의 마지막 허용 거래는 적용한다")
    void appliesBenefit009WhenMonthlyUsageIsLastAllowed() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit009Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
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
    @DisplayName("생활: 월 5회 사용 후에는 적용하지 않는다")
    void rejectsBenefit009WhenMonthlyUsageLimitIsExhausted() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit009Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
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
    @DisplayName("생활: 지정 결제 채널이 아니면 적용하지 않는다")
    void rejectsBenefit009WhenPaymentChannelIsNotEligible() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit009Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "MEDICAL_BEAUTY",
                        /* 신규 발급 실적 유예 여부 */ false,
                        /* 오늘 이미 사용한 횟수 */ 0,
                        /* 이번 달 이미 사용한 횟수 */ 0,
                        /* 대상 가맹점 여부 */ true,
                        /* 지정 결제 채널 여부 */ false));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.PAYMENT_CHANNEL_NOT_ELIGIBLE);
    }

    @Test
    @DisplayName("디지털구독: 온라인·간편결제 범위 제외 - 온라인·앱·배달·간편결제·자동납부 전용 혜택")
    void classifiesBenefit010() {
        // 계산하지 않는 상세도 누락하지 않고 원본 분류 상태와 제외·검토 사유를 고정한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "618",
                /* 카드 내 혜택 순번 */ 10,
                /* benefit_title */ "디지털구독",
                /* 계산 지원 상태 */ "ONLINE_OR_INDIRECT_EXCLUDED",
                /* 분류 사유 */ "온라인·앱·배달·간편결제·자동납부 전용 혜택");
    }

    @Test
    @DisplayName("온라인쇼핑: 온라인·간편결제 범위 제외 - 온라인·앱·배달·간편결제·자동납부 전용 혜택")
    void classifiesBenefit011() {
        // 계산하지 않는 상세도 누락하지 않고 원본 분류 상태와 제외·검토 사유를 고정한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "618",
                /* 카드 내 혜택 순번 */ 11,
                /* benefit_title */ "온라인쇼핑",
                /* 계산 지원 상태 */ "ONLINE_OR_INDIRECT_EXCLUDED",
                /* 분류 사유 */ "온라인·앱·배달·간편결제·자동납부 전용 혜택");
    }

    @Test
    @DisplayName("간편결제: 온라인·간편결제 범위 제외 - 온라인·앱·배달·간편결제·자동납부 전용 혜택")
    void classifiesBenefit012() {
        // 계산하지 않는 상세도 누락하지 않고 원본 분류 상태와 제외·검토 사유를 고정한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "618",
                /* 카드 내 혜택 순번 */ 12,
                /* benefit_title */ "간편결제",
                /* 계산 지원 상태 */ "ONLINE_OR_INDIRECT_EXCLUDED",
                /* 분류 사유 */ "온라인·앱·배달·간편결제·자동납부 전용 혜택");
    }

    @Test
    @DisplayName("해외: 온라인·간편결제 범위 제외 - 온라인·앱·배달·간편결제·자동납부 전용 혜택")
    void classifiesBenefit013() {
        // 계산하지 않는 상세도 누락하지 않고 원본 분류 상태와 제외·검토 사유를 고정한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "618",
                /* 카드 내 혜택 순번 */ 13,
                /* benefit_title */ "해외",
                /* 계산 지원 상태 */ "ONLINE_OR_INDIRECT_EXCLUDED",
                /* 분류 사유 */ "온라인·앱·배달·간편결제·자동납부 전용 혜택");
    }

    @Test
    @DisplayName("유의사항: 계산 규칙이 아닌 상세 정보 - 유의사항·발급·연회비 등 계산 산식이 아닌 상세 정보")
    void classifiesBenefit014() {
        // 계산하지 않는 상세도 누락하지 않고 원본 분류 상태와 제외·검토 사유를 고정한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "618",
                /* 카드 내 혜택 순번 */ 14,
                /* benefit_title */ "유의사항",
                /* 계산 지원 상태 */ "NON_RULE_DETAIL",
                /* 분류 사유 */ "유의사항·발급·연회비 등 계산 산식이 아닌 상세 정보");
    }

    /**
     * benefit_title=대중교통의 계산 조건을 만든다.
     *
     * @param usedMonthlyValue 이번 달에 이미 적용받은 혜택 누적값
     * @return 금액·실적·한도·횟수·적용 조건이 구조화된 혜택 룰
     */
    private BenefitRule benefit002Rule(String usedMonthlyValue) {
        return fixture.rule(
                /* 룰 식별자 */ "card-618-benefit-2",
                /* 할인·캐시백·포인트·마일리지 구분 */ BenefitType.CASHBACK,
                /* 정률·정액·결제 단위·사용량 단위 구분 */ BenefitBasis.RATE,
                /* 혜택 결과 단위 */ RewardUnit.KRW,
                /* 정률 계산 비율 */ "0.05",
                /* 정액 또는 단위당 혜택값 */ "0",
                /* 단위 적립 기준 결제금액 */ "0",
                /* 1회 혜택 인정금액 상한 */ "0",
                /* 혜택 적용 최소 결제금액 */ "0",
                /* 필요한 전월 실적 */ "200000",
                /* 월 혜택 한도 */ "0",
                /* 이번 달에 이미 사용한 혜택 */ usedMonthlyValue,
                /* 시간·요일 조건 */ BenefitPromotionCondition.NONE,
                /* 적용 MOCA 카테고리 */ "TAXI_MOBILITY",
                /* 일 사용 횟수 한도 */ 0,
                /* 월 사용 횟수 한도 */ 0,
                /* 대상 가맹점 확인 필요 여부 */ false,
                /* 지정 결제 채널 확인 필요 여부 */ false);
    }

    /**
     * benefit_title=영화의 계산 조건을 만든다.
     *
     * @param usedMonthlyValue 이번 달에 이미 적용받은 혜택 누적값
     * @return 금액·실적·한도·횟수·적용 조건이 구조화된 혜택 룰
     */
    private BenefitRule benefit004Rule(String usedMonthlyValue) {
        return fixture.rule(
                /* 룰 식별자 */ "card-618-benefit-4",
                /* 할인·캐시백·포인트·마일리지 구분 */ BenefitType.CASHBACK,
                /* 정률·정액·결제 단위·사용량 단위 구분 */ BenefitBasis.FIXED,
                /* 혜택 결과 단위 */ RewardUnit.KRW,
                /* 정률 계산 비율 */ "0",
                /* 정액 또는 단위당 혜택값 */ "5000",
                /* 단위 적립 기준 결제금액 */ "0",
                /* 1회 혜택 인정금액 상한 */ "0",
                /* 혜택 적용 최소 결제금액 */ "20000",
                /* 필요한 전월 실적 */ "200000",
                /* 월 혜택 한도 */ "0",
                /* 이번 달에 이미 사용한 혜택 */ usedMonthlyValue,
                /* 시간·요일 조건 */ BenefitPromotionCondition.NONE,
                /* 적용 MOCA 카테고리 */ "MOVIE_CULTURE",
                /* 일 사용 횟수 한도 */ 0,
                /* 월 사용 횟수 한도 */ 1,
                /* 대상 가맹점 확인 필요 여부 */ true,
                /* 지정 결제 채널 확인 필요 여부 */ false);
    }

    /**
     * benefit_title=편의점의 계산 조건을 만든다.
     *
     * @param usedMonthlyValue 이번 달에 이미 적용받은 혜택 누적값
     * @return 금액·실적·한도·횟수·적용 조건이 구조화된 혜택 룰
     */
    private BenefitRule benefit005Rule(String usedMonthlyValue) {
        return fixture.rule(
                /* 룰 식별자 */ "card-618-benefit-5",
                /* 할인·캐시백·포인트·마일리지 구분 */ BenefitType.CASHBACK,
                /* 정률·정액·결제 단위·사용량 단위 구분 */ BenefitBasis.FIXED,
                /* 혜택 결과 단위 */ RewardUnit.KRW,
                /* 정률 계산 비율 */ "0",
                /* 정액 또는 단위당 혜택값 */ "1000",
                /* 단위 적립 기준 결제금액 */ "0",
                /* 1회 혜택 인정금액 상한 */ "0",
                /* 혜택 적용 최소 결제금액 */ "10000",
                /* 필요한 전월 실적 */ "200000",
                /* 월 혜택 한도 */ "0",
                /* 이번 달에 이미 사용한 혜택 */ usedMonthlyValue,
                /* 시간·요일 조건 */ BenefitPromotionCondition.NONE,
                /* 적용 MOCA 카테고리 */ "CONVENIENCE_STORE",
                /* 일 사용 횟수 한도 */ 1,
                /* 월 사용 횟수 한도 */ 5,
                /* 대상 가맹점 확인 필요 여부 */ false,
                /* 지정 결제 채널 확인 필요 여부 */ true);
    }

    /**
     * benefit_title=카페의 계산 조건을 만든다.
     *
     * @param usedMonthlyValue 이번 달에 이미 적용받은 혜택 누적값
     * @return 금액·실적·한도·횟수·적용 조건이 구조화된 혜택 룰
     */
    private BenefitRule benefit006Rule(String usedMonthlyValue) {
        return fixture.rule(
                /* 룰 식별자 */ "card-618-benefit-6",
                /* 할인·캐시백·포인트·마일리지 구분 */ BenefitType.CASHBACK,
                /* 정률·정액·결제 단위·사용량 단위 구분 */ BenefitBasis.FIXED,
                /* 혜택 결과 단위 */ RewardUnit.KRW,
                /* 정률 계산 비율 */ "0",
                /* 정액 또는 단위당 혜택값 */ "1000",
                /* 단위 적립 기준 결제금액 */ "0",
                /* 1회 혜택 인정금액 상한 */ "0",
                /* 혜택 적용 최소 결제금액 */ "10000",
                /* 필요한 전월 실적 */ "200000",
                /* 월 혜택 한도 */ "0",
                /* 이번 달에 이미 사용한 혜택 */ usedMonthlyValue,
                /* 시간·요일 조건 */ BenefitPromotionCondition.NONE,
                /* 적용 MOCA 카테고리 */ "FOOD_DINING",
                /* 일 사용 횟수 한도 */ 1,
                /* 월 사용 횟수 한도 */ 5,
                /* 대상 가맹점 확인 필요 여부 */ false,
                /* 지정 결제 채널 확인 필요 여부 */ true);
    }

    /**
     * benefit_title=패스트푸드의 계산 조건을 만든다.
     *
     * @param usedMonthlyValue 이번 달에 이미 적용받은 혜택 누적값
     * @return 금액·실적·한도·횟수·적용 조건이 구조화된 혜택 룰
     */
    private BenefitRule benefit007Rule(String usedMonthlyValue) {
        return fixture.rule(
                /* 룰 식별자 */ "card-618-benefit-7",
                /* 할인·캐시백·포인트·마일리지 구분 */ BenefitType.CASHBACK,
                /* 정률·정액·결제 단위·사용량 단위 구분 */ BenefitBasis.FIXED,
                /* 혜택 결과 단위 */ RewardUnit.KRW,
                /* 정률 계산 비율 */ "0",
                /* 정액 또는 단위당 혜택값 */ "1000",
                /* 단위 적립 기준 결제금액 */ "0",
                /* 1회 혜택 인정금액 상한 */ "0",
                /* 혜택 적용 최소 결제금액 */ "10000",
                /* 필요한 전월 실적 */ "200000",
                /* 월 혜택 한도 */ "0",
                /* 이번 달에 이미 사용한 혜택 */ usedMonthlyValue,
                /* 시간·요일 조건 */ BenefitPromotionCondition.NONE,
                /* 적용 MOCA 카테고리 */ "FOOD_DINING",
                /* 일 사용 횟수 한도 */ 1,
                /* 월 사용 횟수 한도 */ 5,
                /* 대상 가맹점 확인 필요 여부 */ false,
                /* 지정 결제 채널 확인 필요 여부 */ true);
    }

    /**
     * benefit_title=생활의 계산 조건을 만든다.
     *
     * @param usedMonthlyValue 이번 달에 이미 적용받은 혜택 누적값
     * @return 금액·실적·한도·횟수·적용 조건이 구조화된 혜택 룰
     */
    private BenefitRule benefit009Rule(String usedMonthlyValue) {
        return fixture.rule(
                /* 룰 식별자 */ "card-618-benefit-9",
                /* 할인·캐시백·포인트·마일리지 구분 */ BenefitType.CASHBACK,
                /* 정률·정액·결제 단위·사용량 단위 구분 */ BenefitBasis.FIXED,
                /* 혜택 결과 단위 */ RewardUnit.KRW,
                /* 정률 계산 비율 */ "0",
                /* 정액 또는 단위당 혜택값 */ "1000",
                /* 단위 적립 기준 결제금액 */ "0",
                /* 1회 혜택 인정금액 상한 */ "0",
                /* 혜택 적용 최소 결제금액 */ "10000",
                /* 필요한 전월 실적 */ "200000",
                /* 월 혜택 한도 */ "0",
                /* 이번 달에 이미 사용한 혜택 */ usedMonthlyValue,
                /* 시간·요일 조건 */ BenefitPromotionCondition.NONE,
                /* 적용 MOCA 카테고리 */ "MEDICAL_BEAUTY",
                /* 일 사용 횟수 한도 */ 1,
                /* 월 사용 횟수 한도 */ 5,
                /* 대상 가맹점 확인 필요 여부 */ false,
                /* 지정 결제 채널 확인 필요 여부 */ true);
    }
}
