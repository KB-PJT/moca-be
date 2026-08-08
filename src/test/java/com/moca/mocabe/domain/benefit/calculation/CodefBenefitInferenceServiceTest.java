package com.moca.mocabe.domain.benefit.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moca.mocabe.domain.benefit.model.BenefitCalculationResult;
import com.moca.mocabe.domain.benefit.model.BenefitInferenceResult;
import com.moca.mocabe.domain.benefit.model.BenefitRewardSummary;
import com.moca.mocabe.domain.benefit.model.BenefitRule;
import com.moca.mocabe.domain.benefit.model.CodefApprovalRecord;
import com.moca.mocabe.domain.benefit.type.BenefitRejectionReason;
import com.moca.mocabe.domain.benefit.type.BenefitType;
import com.moca.mocabe.domain.benefit.type.RewardUnit;

@DisplayName("CODEF 승인내역 기반 혜택 역산")
class CodefBenefitInferenceServiceTest {

    private static final String CONVENIENCE_STORE = "CONVENIENCE_STORE";
    private static final String FOOD_DINING = "FOOD_DINING";

    private final CodefBenefitInferenceService service = new CodefBenefitInferenceService();
    private final MrLifeBenefitTestFixture fixture = new MrLifeBenefitTestFixture();

    @Test
    @DisplayName("CODEF가 혜택 결과를 제공하지 않아도 승인금액과 카테고리로 할인 금액을 역산한다")
    void infersDiscountFromCodefApprovalHistory() {
        BenefitRule rule = fixture.timeRule("mr-life-convenience", CONVENIENCE_STORE, "10000", "10000", 1, 5);

        List<BenefitInferenceResult> results = service.infer(rule, value("314565"), List.of(
                approval("approval-1", "15000", "0", "2026-07-27T10:00:00", null, CONVENIENCE_STORE)));

        BenefitCalculationResult result = results.get(0).calculationResult();
        assertEquals("approval-1", results.get(0).approval().approvalId());
        fixture.assertApplied(result, "1000", "1000", "9000");
    }

    @Test
    @DisplayName("CODEF 실적조회 resCurrentUseAmt를 전월실적 입력값으로 사용해 혜택 적용 여부를 판단한다")
    void usesCodefPerformanceAmountAsPreviousMonthSpend() {
        BenefitRule rule = fixture.timeRule("mr-life-convenience", CONVENIENCE_STORE, "10000", "10000", 1, 5);

        BenefitCalculationResult enoughPerformance = service.infer(rule, value("314565"), List.of(
                approval("approval-1", "10000", "0", "2026-07-27T10:00:00", null, CONVENIENCE_STORE)))
                .get(0)
                .calculationResult();
        BenefitCalculationResult notEnoughPerformance = service.infer(rule, value("299999"), List.of(
                approval("approval-2", "10000", "0", "2026-07-27T10:00:00", null, CONVENIENCE_STORE)))
                .get(0)
                .calculationResult();

        fixture.assertApplied(enoughPerformance, "1000", "1000", "9000");
        fixture.assertRejected(notEnoughPerformance, BenefitRejectionReason.PERFORMANCE_NOT_MET);
    }

    @Test
    @DisplayName("매입일이 있으면 승인시간이 아니라 매입순서대로 월 한도를 먼저 차감한다")
    void replaysApprovalsByCapturedOrder() {
        BenefitRule rule = fixture.timeRule("mr-life-food", FOOD_DINING, "10000", "1000", 0, 10);

        List<BenefitInferenceResult> results = service.infer(rule, value("314565"), List.of(
                approval("approved-first", "10000", "0", "2026-07-27T21:00:00",
                        "2026-07-28T11:00:00", FOOD_DINING),
                approval("captured-first", "10000", "0", "2026-07-27T21:05:00",
                        "2026-07-28T10:00:00", FOOD_DINING)));

        assertEquals("captured-first", results.get(0).approval().approvalId());
        fixture.assertApplied(results.get(0).calculationResult(), "1000", "1000", "0");
        fixture.assertRejected(results.get(1).calculationResult(), BenefitRejectionReason.MONTHLY_LIMIT_EXHAUSTED);
    }

    @Test
    @DisplayName("동일일자 승인내역을 재생해 일 1회와 월 횟수 제한을 역산한다")
    void replaysDailyAndMonthlyUsageCounts() {
        BenefitRule rule = fixture.timeRule("mr-life-convenience", CONVENIENCE_STORE, "10000", "10000", 1, 5);

        List<BenefitInferenceResult> results = service.infer(rule, value("314565"), List.of(
                approval("first", "5000", "0", "2026-07-27T10:00:00", null, CONVENIENCE_STORE),
                approval("second", "5000", "0", "2026-07-27T18:00:00", null, CONVENIENCE_STORE)));

        fixture.assertApplied(results.get(0).calculationResult(), "500", "500", "9500");
        fixture.assertRejected(results.get(1).calculationResult(), BenefitRejectionReason.FREQUENCY_LIMIT_EXHAUSTED);
    }

    @Test
    @DisplayName("CODEF에 없는 할인, 캐시백, 포인트, 마일리지 합계를 계산 결과에서 요약한다")
    void summarizesInferredRewardsByRewardTypeAndUnit() {
        CodefApprovalRecord approval = approval("approval-1", "10000", "0", "2026-07-27T10:00:00", null,
                CONVENIENCE_STORE);
        List<BenefitInferenceResult> results = List.of(
                inferred(approval, result(BenefitType.DISCOUNT, RewardUnit.KRW, "1000", true)),
                inferred(approval, result(BenefitType.CASHBACK, RewardUnit.KRW, "500", true)),
                inferred(approval, result(BenefitType.POINT, RewardUnit.POINT, "120", true)),
                inferred(approval, result(BenefitType.MILEAGE, RewardUnit.MILE, "10", true)),
                inferred(approval, result(BenefitType.DISCOUNT, RewardUnit.KRW, "3000", false)));

        BenefitRewardSummary summary = service.summarize(results);

        fixture.assertBigDecimalEquals("1000", summary.discountAmount());
        fixture.assertBigDecimalEquals("500", summary.cashbackAmount());
        fixture.assertBigDecimalEquals("120", summary.pointAmount());
        fixture.assertBigDecimalEquals("10", summary.mileageAmount());
    }

    private BenefitInferenceResult inferred(CodefApprovalRecord approval, BenefitCalculationResult result) {
        return new BenefitInferenceResult(approval, result);
    }

    private BenefitCalculationResult result(BenefitType benefitType, RewardUnit rewardUnit, String appliedRewardValue,
            boolean applicable) {
        return new BenefitCalculationResult("rule-1", benefitType, rewardUnit, applicable, value(appliedRewardValue),
                value(appliedRewardValue), BigDecimal.ZERO, BenefitRejectionReason.NONE);
    }

    private CodefApprovalRecord approval(String approvalId, String paymentAmount, String usageQuantity,
            String approvedAt, String capturedAt, String category) {
        LocalDateTime capturedDateTime = capturedAt == null ? null : LocalDateTime.parse(capturedAt);
        return new CodefApprovalRecord(approvalId, value(paymentAmount), value(usageQuantity),
                LocalDateTime.parse(approvedAt), capturedDateTime, category, true, true);
    }

    private BigDecimal value(String value) {
        return new BigDecimal(value);
    }
}
