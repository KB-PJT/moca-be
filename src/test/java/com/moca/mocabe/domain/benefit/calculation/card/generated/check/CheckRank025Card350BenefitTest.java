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


@DisplayName("체크 25위 직장인보너스체크카드")
class CheckRank025Card350BenefitTest {

    private static final String NOT_MATCHED_CATEGORY = "__NOT_MATCHED__";

    private final CardBenefitTestFixture fixture = new CardBenefitTestFixture();

    @Test
    @DisplayName("공과금/렌탈: 온라인·간편결제 범위 제외 - 온라인·앱·배달·간편결제·자동납부 전용 혜택")
    void classifiesBenefit001() {
        // 계산하지 않는 상세도 누락하지 않고 원본 분류 상태와 제외·검토 사유를 고정한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "350",
                /* 카드 내 혜택 순번 */ 1,
                /* benefit_title */ "공과금/렌탈",
                /* 계산 지원 상태 */ "ONLINE_OR_INDIRECT_EXCLUDED",
                /* 분류 사유 */ "온라인·앱·배달·간편결제·자동납부 전용 혜택");
    }

    @Test
    @DisplayName("주유소: 사용 단위당 50원 할인 정상 적용")
    void appliesBenefit002() {
        // 테스트에 사용한 계산 규칙이 카드고릴라의 해당 혜택 상세에서 만들어졌는지 확인한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "350",
                /* 카드 내 혜택 순번 */ 2,
                /* benefit_title */ "주유소",
                /* 계산 지원 상태 */ "DIRECT_OFFLINE_CALCULABLE",
                /* 분류 사유 */ "직접 카드 결제 혜택 산식 계산 가능");

        // 카드 혜택 룰과 현재 결제 상황을 조합해 예상 혜택을 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit002Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "40",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "FUEL_CAR"));

        // 월 한도 반영 전 혜택, 실제 적용 혜택, 계산 후 남은 월 한도를 차례로 검증한다.
        fixture.assertApplied(
                result,
                /* 월 한도 반영 전 혜택 */ "2000",
                /* 실제 적용 혜택 */ "2000",
                /* 남은 월 한도 */ "0");
    }

    @Test
    @DisplayName("주유소: 카테고리가 일치하지 않으면 적용하지 않는다")
    void rejectsBenefit002WhenCategoryIsNotMatched() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit002Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
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
    @DisplayName("주유소: 전월 실적 200,000원보다 1원 적으면 적용하지 않는다")
    void rejectsBenefit002WhenPerformanceIsOneWonBelowRequirement() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit002Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "40",
                        /* 전월 실적 */ "199999",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "FUEL_CAR"));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.PERFORMANCE_NOT_MET);
    }

    @Test
    @DisplayName("주유소: 전월 실적 200,000원부터 적용한다")
    void appliesBenefit002WhenPerformanceEqualsRequirement() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit002Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "40",
                        /* 전월 실적 */ "200000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "FUEL_CAR"));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("주유소: 금요일 23시 59분 59초에는 주말 혜택을 적용하지 않는다")
    void rejectsBenefit002WhenBeforeWeekend() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit002Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "40",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-24T23:59:59",
                        /* MOCA 가맹점 카테고리 */ "FUEL_CAR"));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.CONDITION_NOT_MET);
    }

    @Test
    @DisplayName("주유소: 토요일 00시 정각부터 주말 혜택을 적용한다")
    void appliesBenefit002WhenAtWeekendStart() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit002Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "40",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T00:00:00",
                        /* MOCA 가맹점 카테고리 */ "FUEL_CAR"));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("주유소: 일요일 23시 59분 59초까지 주말 혜택을 적용한다")
    void appliesBenefit002WhenBeforeWeekendEnd() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit002Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "40",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-26T23:59:59",
                        /* MOCA 가맹점 카테고리 */ "FUEL_CAR"));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("주유소: 월요일 00시 정각부터 주말 혜택을 적용하지 않는다")
    void rejectsBenefit002WhenAtWeekendEnd() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit002Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "40",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-27T00:00:00",
                        /* MOCA 가맹점 카테고리 */ "FUEL_CAR"));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.CONDITION_NOT_MET);
    }

    @Test
    @DisplayName("주유소: 사용량이 0이면 혜택도 0으로 계산한다")
    void calculatesZeroBenefit002ForZeroUsage() {
        // 리터·횟수처럼 사용량을 기준으로 계산하는 혜택에 사용량 0을 입력한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit002Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
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
    @DisplayName("정비: 10% 할인 정상 적용")
    void appliesBenefit003() {
        // 테스트에 사용한 계산 규칙이 카드고릴라의 해당 혜택 상세에서 만들어졌는지 확인한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "350",
                /* 카드 내 혜택 순번 */ 3,
                /* benefit_title */ "정비",
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
                        /* MOCA 가맹점 카테고리 */ ""));

        // 월 한도 반영 전 혜택, 실제 적용 혜택, 계산 후 남은 월 한도를 차례로 검증한다.
        fixture.assertApplied(
                result,
                /* 월 한도 반영 전 혜택 */ "10000",
                /* 실제 적용 혜택 */ "10000",
                /* 남은 월 한도 */ "0");
    }

    @Test
    @DisplayName("정비: 전월 실적 300,000원보다 1원 적으면 적용하지 않는다")
    void rejectsBenefit003WhenPerformanceIsOneWonBelowRequirement() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit003Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "299999",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ ""));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.PERFORMANCE_NOT_MET);
    }

    @Test
    @DisplayName("정비: 전월 실적 300,000원부터 적용한다")
    void appliesBenefit003WhenPerformanceEqualsRequirement() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit003Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "300000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ ""));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("정비: 정률 혜택의 원 미만 금액을 절사한다")
    void floorsBenefit003FractionalReward() {
        // 비율 계산 결과에 원 미만 소수가 생기는 결제금액으로 절사 정책을 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit003Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "10001",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ ""));

        // 카드 혜택은 원 미만 금액을 올림하지 않고 버린 결과와 같아야 한다.
        fixture.assertApplied(result);
        fixture.assertBigDecimalEquals("1000", result.rawRewardValue());
    }

    @Test
    @DisplayName("렌터카: 직접 카드 결제 계산 규칙 검토 필요 - 계산 가능한 할인/캐시백/포인트/마일리지 패턴을 자동 추출하지 못함")
    void classifiesBenefit004() {
        // 계산하지 않는 상세도 누락하지 않고 원본 분류 상태와 제외·검토 사유를 고정한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "350",
                /* 카드 내 혜택 순번 */ 4,
                /* benefit_title */ "렌터카",
                /* 계산 지원 상태 */ "DIRECT_OFFLINE_REVIEW_REQUIRED",
                /* 분류 사유 */ "계산 가능한 할인/캐시백/포인트/마일리지 패턴을 자동 추출하지 못함");
    }

    @Test
    @DisplayName("백화점: 5% 할인 정상 적용")
    void appliesBenefit005() {
        // 테스트에 사용한 계산 규칙이 카드고릴라의 해당 혜택 상세에서 만들어졌는지 확인한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "350",
                /* 카드 내 혜택 순번 */ 5,
                /* benefit_title */ "백화점",
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
                        /* MOCA 가맹점 카테고리 */ ""));

        // 월 한도 반영 전 혜택, 실제 적용 혜택, 계산 후 남은 월 한도를 차례로 검증한다.
        fixture.assertApplied(
                result,
                /* 월 한도 반영 전 혜택 */ "5000",
                /* 실제 적용 혜택 */ "5000",
                /* 남은 월 한도 */ "0");
    }

    @Test
    @DisplayName("백화점: 전월 실적 500,000원보다 1원 적으면 적용하지 않는다")
    void rejectsBenefit005WhenPerformanceIsOneWonBelowRequirement() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit005Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
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
    @DisplayName("백화점: 전월 실적 500,000원부터 적용한다")
    void appliesBenefit005WhenPerformanceEqualsRequirement() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit005Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
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
    @DisplayName("백화점: 정률 혜택의 원 미만 금액을 절사한다")
    void floorsBenefit005FractionalReward() {
        // 비율 계산 결과에 원 미만 소수가 생기는 결제금액으로 절사 정책을 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit005Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "10001",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ ""));

        // 카드 혜택은 원 미만 금액을 올림하지 않고 버린 결과와 같아야 한다.
        fixture.assertApplied(result);
        fixture.assertBigDecimalEquals("500", result.rawRewardValue());
    }

    @Test
    @DisplayName("패밀리레스토랑: 10% 할인 정상 적용")
    void appliesBenefit006() {
        // 테스트에 사용한 계산 규칙이 카드고릴라의 해당 혜택 상세에서 만들어졌는지 확인한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "350",
                /* 카드 내 혜택 순번 */ 6,
                /* benefit_title */ "패밀리레스토랑",
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
                /* 월 한도 반영 전 혜택 */ "10000",
                /* 실제 적용 혜택 */ "10000",
                /* 남은 월 한도 */ "0");
    }

    @Test
    @DisplayName("패밀리레스토랑: 전월 실적 200,000원보다 1원 적으면 적용하지 않는다")
    void rejectsBenefit006WhenPerformanceIsOneWonBelowRequirement() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit006Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "199999",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ ""));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.PERFORMANCE_NOT_MET);
    }

    @Test
    @DisplayName("패밀리레스토랑: 전월 실적 200,000원부터 적용한다")
    void appliesBenefit006WhenPerformanceEqualsRequirement() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit006Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "200000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ ""));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("패밀리레스토랑: 지정 결제 채널이 아니면 적용하지 않는다")
    void rejectsBenefit006WhenPaymentChannelIsNotEligible() {
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
                        /* 이번 달 이미 사용한 횟수 */ 0,
                        /* 대상 가맹점 여부 */ true,
                        /* 지정 결제 채널 여부 */ false));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.PAYMENT_CHANNEL_NOT_ELIGIBLE);
    }

    @Test
    @DisplayName("패밀리레스토랑: 정률 혜택의 원 미만 금액을 절사한다")
    void floorsBenefit006FractionalReward() {
        // 비율 계산 결과에 원 미만 소수가 생기는 결제금액으로 절사 정책을 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit006Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "10001",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ ""));

        // 카드 혜택은 원 미만 금액을 올림하지 않고 버린 결과와 같아야 한다.
        fixture.assertApplied(result);
        fixture.assertBigDecimalEquals("1000", result.rawRewardValue());
    }

    @Test
    @DisplayName("대중교통: 5% 할인 정상 적용")
    void appliesBenefit007() {
        // 테스트에 사용한 계산 규칙이 카드고릴라의 해당 혜택 상세에서 만들어졌는지 확인한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "350",
                /* 카드 내 혜택 순번 */ 7,
                /* benefit_title */ "대중교통",
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
                        /* MOCA 가맹점 카테고리 */ "TAXI_MOBILITY"));

        // 월 한도 반영 전 혜택, 실제 적용 혜택, 계산 후 남은 월 한도를 차례로 검증한다.
        fixture.assertApplied(
                result,
                /* 월 한도 반영 전 혜택 */ "1000",
                /* 실제 적용 혜택 */ "1000",
                /* 남은 월 한도 */ "0");
    }

    @Test
    @DisplayName("대중교통: 카테고리가 일치하지 않으면 적용하지 않는다")
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
    @DisplayName("대중교통: 전월 실적 300,000원보다 1원 적으면 적용하지 않는다")
    void rejectsBenefit007WhenPerformanceIsOneWonBelowRequirement() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit007Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
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
    @DisplayName("대중교통: 전월 실적 300,000원부터 적용한다")
    void appliesBenefit007WhenPerformanceEqualsRequirement() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit007Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
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
    @DisplayName("대중교통: 1회 인정금액 20,000원 초과분은 혜택에서 제외한다")
    void capsBenefit007BenefitBaseAmount() {
        // 1회 인정금액과 정확히 같은 결제의 혜택을 계산한다.
        BenefitCalculationResult atLimit = fixture.calculator.calculate(
                benefit007Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "20000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "TAXI_MOBILITY"));

        // 인정금액보다 1원 큰 결제로 초과분이 계산 기준에서 제외되는지 확인한다.
        BenefitCalculationResult overLimit = fixture.calculator.calculate(
                benefit007Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "20001",
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
    @DisplayName("대중교통: 정률 혜택의 원 미만 금액을 절사한다")
    void floorsBenefit007FractionalReward() {
        // 비율 계산 결과에 원 미만 소수가 생기는 결제금액으로 절사 정책을 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit007Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
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
    void classifiesBenefit008() {
        // 계산하지 않는 상세도 누락하지 않고 원본 분류 상태와 제외·검토 사유를 고정한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "350",
                /* 카드 내 혜택 순번 */ 8,
                /* benefit_title */ "통신",
                /* 계산 지원 상태 */ "ONLINE_OR_INDIRECT_EXCLUDED",
                /* 분류 사유 */ "온라인·앱·배달·간편결제·자동납부 전용 혜택");
    }

    @Test
    @DisplayName("테마파크: 50% 할인 정상 적용")
    void appliesBenefit009() {
        // 테스트에 사용한 계산 규칙이 카드고릴라의 해당 혜택 상세에서 만들어졌는지 확인한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "350",
                /* 카드 내 혜택 순번 */ 9,
                /* benefit_title */ "테마파크",
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
                        /* MOCA 가맹점 카테고리 */ ""));

        // 월 한도 반영 전 혜택, 실제 적용 혜택, 계산 후 남은 월 한도를 차례로 검증한다.
        fixture.assertApplied(
                result,
                /* 월 한도 반영 전 혜택 */ "25000",
                /* 실제 적용 혜택 */ "25000",
                /* 남은 월 한도 */ "0");
    }

    @Test
    @DisplayName("테마파크: 전월 실적 200,000원보다 1원 적으면 적용하지 않는다")
    void rejectsBenefit009WhenPerformanceIsOneWonBelowRequirement() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit009Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "199999",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ ""));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.PERFORMANCE_NOT_MET);
    }

    @Test
    @DisplayName("테마파크: 전월 실적 200,000원부터 적용한다")
    void appliesBenefit009WhenPerformanceEqualsRequirement() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit009Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "200000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ ""));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("테마파크: 1회 인정금액 50,000원 초과분은 혜택에서 제외한다")
    void capsBenefit009BenefitBaseAmount() {
        // 1회 인정금액과 정확히 같은 결제의 혜택을 계산한다.
        BenefitCalculationResult atLimit = fixture.calculator.calculate(
                benefit009Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "50000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ ""));

        // 인정금액보다 1원 큰 결제로 초과분이 계산 기준에서 제외되는지 확인한다.
        BenefitCalculationResult overLimit = fixture.calculator.calculate(
                benefit009Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "50001",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ ""));

        // 두 결제의 혜택이 같으면 1회 인정금액 상한이 정상 적용된 것이다.
        fixture.assertApplied(atLimit);
        fixture.assertApplied(overLimit);
        fixture.assertBigDecimalEquals(atLimit.rawRewardValue(), overLimit.rawRewardValue());
    }

    @Test
    @DisplayName("테마파크: 정률 혜택의 원 미만 금액을 절사한다")
    void floorsBenefit009FractionalReward() {
        // 비율 계산 결과에 원 미만 소수가 생기는 결제금액으로 절사 정책을 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit009Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "10001",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ ""));

        // 카드 혜택은 원 미만 금액을 올림하지 않고 버린 결과와 같아야 한다.
        fixture.assertApplied(result);
        fixture.assertBigDecimalEquals("5000", result.rawRewardValue());
    }

    @Test
    @DisplayName("해피포인트: 온라인·간편결제 범위 제외 - 온라인·앱·배달·간편결제·자동납부 전용 혜택")
    void classifiesBenefit010() {
        // 계산하지 않는 상세도 누락하지 않고 원본 분류 상태와 제외·검토 사유를 고정한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "350",
                /* 카드 내 혜택 순번 */ 10,
                /* benefit_title */ "해피포인트",
                /* 계산 지원 상태 */ "ONLINE_OR_INDIRECT_EXCLUDED",
                /* 분류 사유 */ "온라인·앱·배달·간편결제·자동납부 전용 혜택");
    }

    @Test
    @DisplayName("기타: 200000원 정액 할인 정상 적용")
    void appliesBenefit011() {
        // 테스트에 사용한 계산 규칙이 카드고릴라의 해당 혜택 상세에서 만들어졌는지 확인한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "350",
                /* 카드 내 혜택 순번 */ 11,
                /* benefit_title */ "기타",
                /* 계산 지원 상태 */ "DIRECT_OFFLINE_CALCULABLE",
                /* 분류 사유 */ "직접 카드 결제 혜택 산식 계산 가능");

        // 카드 혜택 룰과 현재 결제 상황을 조합해 예상 혜택을 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit011Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ ""));

        // 월 한도 반영 전 혜택, 실제 적용 혜택, 계산 후 남은 월 한도를 차례로 검증한다.
        fixture.assertApplied(
                result,
                /* 월 한도 반영 전 혜택 */ "100000",
                /* 실제 적용 혜택 */ "100000",
                /* 남은 월 한도 */ "0");
    }

    @Test
    @DisplayName("기타: 전월 실적 200,000원보다 1원 적으면 적용하지 않는다")
    void rejectsBenefit011WhenPerformanceIsOneWonBelowRequirement() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit011Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "199999",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ ""));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.PERFORMANCE_NOT_MET);
    }

    @Test
    @DisplayName("기타: 전월 실적 200,000원부터 적용한다")
    void appliesBenefit011WhenPerformanceEqualsRequirement() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit011Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "200000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ ""));

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }

    @Test
    @DisplayName("기타: 신규 발급 유예기간에는 전월 실적 0원도 적용한다")
    void appliesBenefit011WhenNewMemberGracePeriodIsActive() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit011Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
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
    @DisplayName("기타: 제외 가맹점에서는 적용하지 않는다")
    void rejectsBenefit011WhenMerchantIsNotEligible() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit011Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
                fixture.context(
                        /* 결제금액 */ "100000",
                        /* 리터·횟수 등 사용량 */ "0",
                        /* 전월 실적 */ "10000000",
                        /* 카드 승인 시각 */ "2026-07-25T22:00:00",
                        /* MOCA 가맹점 카테고리 */ "",
                        /* 신규 발급 실적 유예 여부 */ false,
                        /* 오늘 이미 사용한 횟수 */ 0,
                        /* 이번 달 이미 사용한 횟수 */ 0,
                        /* 대상 가맹점 여부 */ false,
                        /* 지정 결제 채널 여부 */ true));

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.MERCHANT_NOT_ELIGIBLE);
    }

    @Test
    @DisplayName("기타: 정액 할인은 결제금액을 초과하지 않는다")
    void capsBenefit011FixedDiscountAtPaymentAmount() {
        // 정액 할인액보다 작은 결제금액으로 결제금액 초과 할인 방지 정책을 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                benefit011Rule(/* 이번 달에 이미 사용한 혜택 */ "0"),
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
    @DisplayName("유의사항: 계산 규칙이 아닌 상세 정보 - 유의사항·발급·연회비 등 계산 산식이 아닌 상세 정보")
    void classifiesBenefit012() {
        // 계산하지 않는 상세도 누락하지 않고 원본 분류 상태와 제외·검토 사유를 고정한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "350",
                /* 카드 내 혜택 순번 */ 12,
                /* benefit_title */ "유의사항",
                /* 계산 지원 상태 */ "NON_RULE_DETAIL",
                /* 분류 사유 */ "유의사항·발급·연회비 등 계산 산식이 아닌 상세 정보");
    }

    /**
     * benefit_title=주유소의 계산 조건을 만든다.
     *
     * @param usedMonthlyValue 이번 달에 이미 적용받은 혜택 누적값
     * @return 금액·실적·한도·횟수·적용 조건이 구조화된 혜택 룰
     */
    private BenefitRule benefit002Rule(String usedMonthlyValue) {
        return fixture.rule(
                /* 룰 식별자 */ "card-350-benefit-2",
                /* 할인·캐시백·포인트·마일리지 구분 */ BenefitType.DISCOUNT,
                /* 정률·정액·결제 단위·사용량 단위 구분 */ BenefitBasis.PER_USAGE_UNIT,
                /* 혜택 결과 단위 */ RewardUnit.KRW,
                /* 정률 계산 비율 */ "0",
                /* 정액 또는 단위당 혜택값 */ "50",
                /* 단위 적립 기준 결제금액 */ "0",
                /* 1회 혜택 인정금액 상한 */ "0",
                /* 혜택 적용 최소 결제금액 */ "0",
                /* 필요한 전월 실적 */ "200000",
                /* 월 혜택 한도 */ "0",
                /* 이번 달에 이미 사용한 혜택 */ usedMonthlyValue,
                /* 시간·요일 조건 */ BenefitPromotionCondition.WEEKEND,
                /* 적용 MOCA 카테고리 */ "FUEL_CAR",
                /* 일 사용 횟수 한도 */ 0,
                /* 월 사용 횟수 한도 */ 0,
                /* 대상 가맹점 확인 필요 여부 */ false,
                /* 지정 결제 채널 확인 필요 여부 */ false);
    }

    /**
     * benefit_title=정비의 계산 조건을 만든다.
     *
     * @param usedMonthlyValue 이번 달에 이미 적용받은 혜택 누적값
     * @return 금액·실적·한도·횟수·적용 조건이 구조화된 혜택 룰
     */
    private BenefitRule benefit003Rule(String usedMonthlyValue) {
        return fixture.rule(
                /* 룰 식별자 */ "card-350-benefit-3",
                /* 할인·캐시백·포인트·마일리지 구분 */ BenefitType.DISCOUNT,
                /* 정률·정액·결제 단위·사용량 단위 구분 */ BenefitBasis.RATE,
                /* 혜택 결과 단위 */ RewardUnit.KRW,
                /* 정률 계산 비율 */ "0.1",
                /* 정액 또는 단위당 혜택값 */ "0",
                /* 단위 적립 기준 결제금액 */ "0",
                /* 1회 혜택 인정금액 상한 */ "0",
                /* 혜택 적용 최소 결제금액 */ "0",
                /* 필요한 전월 실적 */ "300000",
                /* 월 혜택 한도 */ "0",
                /* 이번 달에 이미 사용한 혜택 */ usedMonthlyValue,
                /* 시간·요일 조건 */ BenefitPromotionCondition.NONE,
                /* 적용 MOCA 카테고리 */ "",
                /* 일 사용 횟수 한도 */ 0,
                /* 월 사용 횟수 한도 */ 0,
                /* 대상 가맹점 확인 필요 여부 */ false,
                /* 지정 결제 채널 확인 필요 여부 */ false);
    }

    /**
     * benefit_title=백화점의 계산 조건을 만든다.
     *
     * @param usedMonthlyValue 이번 달에 이미 적용받은 혜택 누적값
     * @return 금액·실적·한도·횟수·적용 조건이 구조화된 혜택 룰
     */
    private BenefitRule benefit005Rule(String usedMonthlyValue) {
        return fixture.rule(
                /* 룰 식별자 */ "card-350-benefit-5",
                /* 할인·캐시백·포인트·마일리지 구분 */ BenefitType.DISCOUNT,
                /* 정률·정액·결제 단위·사용량 단위 구분 */ BenefitBasis.RATE,
                /* 혜택 결과 단위 */ RewardUnit.KRW,
                /* 정률 계산 비율 */ "0.05",
                /* 정액 또는 단위당 혜택값 */ "0",
                /* 단위 적립 기준 결제금액 */ "0",
                /* 1회 혜택 인정금액 상한 */ "0",
                /* 혜택 적용 최소 결제금액 */ "0",
                /* 필요한 전월 실적 */ "500000",
                /* 월 혜택 한도 */ "0",
                /* 이번 달에 이미 사용한 혜택 */ usedMonthlyValue,
                /* 시간·요일 조건 */ BenefitPromotionCondition.NONE,
                /* 적용 MOCA 카테고리 */ "",
                /* 일 사용 횟수 한도 */ 0,
                /* 월 사용 횟수 한도 */ 0,
                /* 대상 가맹점 확인 필요 여부 */ false,
                /* 지정 결제 채널 확인 필요 여부 */ false);
    }

    /**
     * benefit_title=패밀리레스토랑의 계산 조건을 만든다.
     *
     * @param usedMonthlyValue 이번 달에 이미 적용받은 혜택 누적값
     * @return 금액·실적·한도·횟수·적용 조건이 구조화된 혜택 룰
     */
    private BenefitRule benefit006Rule(String usedMonthlyValue) {
        return fixture.rule(
                /* 룰 식별자 */ "card-350-benefit-6",
                /* 할인·캐시백·포인트·마일리지 구분 */ BenefitType.DISCOUNT,
                /* 정률·정액·결제 단위·사용량 단위 구분 */ BenefitBasis.RATE,
                /* 혜택 결과 단위 */ RewardUnit.KRW,
                /* 정률 계산 비율 */ "0.1",
                /* 정액 또는 단위당 혜택값 */ "0",
                /* 단위 적립 기준 결제금액 */ "0",
                /* 1회 혜택 인정금액 상한 */ "0",
                /* 혜택 적용 최소 결제금액 */ "0",
                /* 필요한 전월 실적 */ "200000",
                /* 월 혜택 한도 */ "0",
                /* 이번 달에 이미 사용한 혜택 */ usedMonthlyValue,
                /* 시간·요일 조건 */ BenefitPromotionCondition.NONE,
                /* 적용 MOCA 카테고리 */ "",
                /* 일 사용 횟수 한도 */ 0,
                /* 월 사용 횟수 한도 */ 0,
                /* 대상 가맹점 확인 필요 여부 */ false,
                /* 지정 결제 채널 확인 필요 여부 */ true);
    }

    /**
     * benefit_title=대중교통의 계산 조건을 만든다.
     *
     * @param usedMonthlyValue 이번 달에 이미 적용받은 혜택 누적값
     * @return 금액·실적·한도·횟수·적용 조건이 구조화된 혜택 룰
     */
    private BenefitRule benefit007Rule(String usedMonthlyValue) {
        return fixture.rule(
                /* 룰 식별자 */ "card-350-benefit-7",
                /* 할인·캐시백·포인트·마일리지 구분 */ BenefitType.DISCOUNT,
                /* 정률·정액·결제 단위·사용량 단위 구분 */ BenefitBasis.RATE,
                /* 혜택 결과 단위 */ RewardUnit.KRW,
                /* 정률 계산 비율 */ "0.05",
                /* 정액 또는 단위당 혜택값 */ "0",
                /* 단위 적립 기준 결제금액 */ "0",
                /* 1회 혜택 인정금액 상한 */ "20000",
                /* 혜택 적용 최소 결제금액 */ "0",
                /* 필요한 전월 실적 */ "300000",
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
     * benefit_title=테마파크의 계산 조건을 만든다.
     *
     * @param usedMonthlyValue 이번 달에 이미 적용받은 혜택 누적값
     * @return 금액·실적·한도·횟수·적용 조건이 구조화된 혜택 룰
     */
    private BenefitRule benefit009Rule(String usedMonthlyValue) {
        return fixture.rule(
                /* 룰 식별자 */ "card-350-benefit-9",
                /* 할인·캐시백·포인트·마일리지 구분 */ BenefitType.DISCOUNT,
                /* 정률·정액·결제 단위·사용량 단위 구분 */ BenefitBasis.RATE,
                /* 혜택 결과 단위 */ RewardUnit.KRW,
                /* 정률 계산 비율 */ "0.5",
                /* 정액 또는 단위당 혜택값 */ "0",
                /* 단위 적립 기준 결제금액 */ "0",
                /* 1회 혜택 인정금액 상한 */ "50000",
                /* 혜택 적용 최소 결제금액 */ "0",
                /* 필요한 전월 실적 */ "200000",
                /* 월 혜택 한도 */ "0",
                /* 이번 달에 이미 사용한 혜택 */ usedMonthlyValue,
                /* 시간·요일 조건 */ BenefitPromotionCondition.NONE,
                /* 적용 MOCA 카테고리 */ "",
                /* 일 사용 횟수 한도 */ 0,
                /* 월 사용 횟수 한도 */ 0,
                /* 대상 가맹점 확인 필요 여부 */ false,
                /* 지정 결제 채널 확인 필요 여부 */ false);
    }

    /**
     * benefit_title=기타의 계산 조건을 만든다.
     *
     * @param usedMonthlyValue 이번 달에 이미 적용받은 혜택 누적값
     * @return 금액·실적·한도·횟수·적용 조건이 구조화된 혜택 룰
     */
    private BenefitRule benefit011Rule(String usedMonthlyValue) {
        return fixture.rule(
                /* 룰 식별자 */ "card-350-benefit-11",
                /* 할인·캐시백·포인트·마일리지 구분 */ BenefitType.DISCOUNT,
                /* 정률·정액·결제 단위·사용량 단위 구분 */ BenefitBasis.FIXED,
                /* 혜택 결과 단위 */ RewardUnit.KRW,
                /* 정률 계산 비율 */ "0",
                /* 정액 또는 단위당 혜택값 */ "200000",
                /* 단위 적립 기준 결제금액 */ "0",
                /* 1회 혜택 인정금액 상한 */ "0",
                /* 혜택 적용 최소 결제금액 */ "0",
                /* 필요한 전월 실적 */ "200000",
                /* 월 혜택 한도 */ "0",
                /* 이번 달에 이미 사용한 혜택 */ usedMonthlyValue,
                /* 시간·요일 조건 */ BenefitPromotionCondition.NONE,
                /* 적용 MOCA 카테고리 */ "",
                /* 일 사용 횟수 한도 */ 0,
                /* 월 사용 횟수 한도 */ 0,
                /* 대상 가맹점 확인 필요 여부 */ true,
                /* 지정 결제 채널 확인 필요 여부 */ false);
    }
}
