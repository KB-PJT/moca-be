package com.moca.mocabe.domain.benefit.calculation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.moca.mocabe.domain.benefit.model.BenefitCalculationContext;
import com.moca.mocabe.domain.benefit.model.BenefitCalculationResult;
import com.moca.mocabe.domain.benefit.model.BenefitInferenceResult;
import com.moca.mocabe.domain.benefit.model.BenefitRewardSummary;
import com.moca.mocabe.domain.benefit.model.BenefitRule;
import com.moca.mocabe.domain.benefit.model.CodefApprovalRecord;
import com.moca.mocabe.domain.benefit.type.BenefitType;
import com.moca.mocabe.domain.benefit.type.RewardUnit;

/**
 * CODEF 승인내역에는 실제 할인·캐시백·마일리지 결과가 없으므로 우리 룰로 혜택을 역산한다.
 */
public class CodefBenefitInferenceService {

    private final BenefitCalculator calculator;

    public CodefBenefitInferenceService() {
        this(new PromotionBenefitCalculator());
    }

    public CodefBenefitInferenceService(BenefitCalculator calculator) {
        this.calculator = calculator;
    }

    public List<BenefitInferenceResult> infer(BenefitRule rule, BigDecimal previousMonthSpend,
            List<CodefApprovalRecord> approvals) {
        List<CodefApprovalRecord> orderedApprovals = approvals.stream()
                .sorted(Comparator.comparing(this::orderingAt))
                .toList();
        List<BenefitInferenceResult> results = new ArrayList<>();
        Map<LocalDate, Integer> dailyAppliedCounts = new HashMap<>();
        BigDecimal usedMonthlyReward = rule.usedMonthlyValue();
        int monthlyAppliedCount = 0;

        for (CodefApprovalRecord approval : orderedApprovals) {
            LocalDate approvedDate = approval.approvedAt().toLocalDate();
            BenefitCalculationContext context = new BenefitCalculationContext(approval.paymentAmount(),
                    approval.usageQuantity(), previousMonthSpend, approval.approvedAt(), approval.mocaCategory(),
                    false, dailyAppliedCounts.getOrDefault(approvedDate, 0), monthlyAppliedCount,
                    approval.merchantEligible(), approval.paymentChannelEligible());
            BenefitCalculationResult calculationResult = calculator.calculate(withUsedMonthly(rule, usedMonthlyReward),
                    context);

            results.add(new BenefitInferenceResult(approval, calculationResult));
            if (calculationResult.applicable()) {
                usedMonthlyReward = usedMonthlyReward.add(calculationResult.appliedRewardValue());
                dailyAppliedCounts.merge(approvedDate, 1, Integer::sum);
                monthlyAppliedCount++;
            }
        }

        return results;
    }

    public BenefitRewardSummary summarize(List<BenefitInferenceResult> results) {
        BigDecimal discountAmount = BigDecimal.ZERO;
        BigDecimal cashbackAmount = BigDecimal.ZERO;
        BigDecimal pointAmount = BigDecimal.ZERO;
        BigDecimal mileageAmount = BigDecimal.ZERO;

        for (BenefitInferenceResult result : results) {
            BenefitCalculationResult calculationResult = result.calculationResult();
            if (calculationResult.applicable()) {
                if (calculationResult.benefitType() == BenefitType.DISCOUNT) {
                    discountAmount = discountAmount.add(calculationResult.appliedRewardValue());
                }
                if (calculationResult.benefitType() == BenefitType.CASHBACK) {
                    cashbackAmount = cashbackAmount.add(calculationResult.appliedRewardValue());
                }
                if (calculationResult.rewardUnit() == RewardUnit.POINT) {
                    pointAmount = pointAmount.add(calculationResult.appliedRewardValue());
                }
                if (calculationResult.rewardUnit() == RewardUnit.MILE) {
                    mileageAmount = mileageAmount.add(calculationResult.appliedRewardValue());
                }
            }
        }

        return new BenefitRewardSummary(discountAmount, cashbackAmount, pointAmount, mileageAmount);
    }

    private LocalDateTime orderingAt(CodefApprovalRecord approval) {
        return approval.capturedAt() == null ? approval.approvedAt() : approval.capturedAt();
    }

    private BenefitRule withUsedMonthly(BenefitRule rule, BigDecimal usedMonthlyValue) {
        return new BenefitRule(rule.ruleId(), rule.benefitType(), rule.benefitBasis(), rule.rewardUnit(),
                rule.rewardRate(), rule.rewardValue(), rule.spendUnitAmount(), rule.maximumBenefitBaseAmount(),
                rule.minimumPaymentAmount(), rule.requiredPreviousMonthSpend(), rule.monthlyLimitValue(),
                usedMonthlyValue, rule.promotionCondition(), rule.mocaCategories(), rule.dailyUsageLimit(),
                rule.monthlyUsageLimit(), rule.merchantEligibilityRequired(),
                rule.paymentChannelEligibilityRequired());
    }
}
