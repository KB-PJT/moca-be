package com.moca.mocabe.domain.benefit.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.moca.mocabe.domain.benefit.model.BenefitCalculationContext;
import com.moca.mocabe.domain.benefit.model.BenefitCalculationResult;
import com.moca.mocabe.domain.benefit.model.BenefitRule;
import com.moca.mocabe.domain.benefit.type.BenefitRejectionReason;
import com.moca.mocabe.domain.benefit.type.BenefitType;
import com.moca.mocabe.domain.benefit.type.RewardUnit;

/**
 * 시간, 요일, 채널 같은 추가 조건을 제외한 기본 혜택 계산기다.
 */
public class BasicBenefitCalculator implements BenefitCalculator {

    @Override
    public BenefitCalculationResult calculate(BenefitRule rule, BenefitCalculationContext context) {
        // 계산보다 먼저 적용 가능 조건을 확인해 사용자에게 명확한 미적용 사유를 돌려준다.
        if (rule.merchantEligibilityRequired() && !context.merchantEligible()) {
            return reject(rule, BenefitRejectionReason.MERCHANT_NOT_ELIGIBLE);
        }

        if (rule.paymentChannelEligibilityRequired() && !context.paymentChannelEligible()) {
            return reject(rule, BenefitRejectionReason.PAYMENT_CHANNEL_NOT_ELIGIBLE);
        }

        if (rule.dailyUsageLimit() > 0 && context.usedDailyCount() >= rule.dailyUsageLimit()) {
            return reject(rule, BenefitRejectionReason.FREQUENCY_LIMIT_EXHAUSTED);
        }

        if (rule.monthlyUsageLimit() > 0 && context.usedMonthlyCount() >= rule.monthlyUsageLimit()) {
            return reject(rule, BenefitRejectionReason.FREQUENCY_LIMIT_EXHAUSTED);
        }

        if (!rule.matchesCategory(context.mocaCategory())) {
            return reject(rule, BenefitRejectionReason.CATEGORY_NOT_MATCHED);
        }

        if (isLessThan(context.paymentAmount(), rule.minimumPaymentAmount())) {
            return reject(rule, BenefitRejectionReason.MIN_PAYMENT_NOT_MET);
        }

        if (!context.newMemberGracePeriod()
                && isLessThan(context.previousMonthSpend(), rule.requiredPreviousMonthSpend())) {
            return reject(rule, BenefitRejectionReason.PERFORMANCE_NOT_MET);
        }

        // rawRewardValue는 이론상 혜택이고, appliedRewardValue는 월 한도 반영 후 실제 혜택이다.
        BigDecimal rawRewardValue = capPaymentAmount(rule, calculateRawReward(rule, context), context.paymentAmount());
        BigDecimal appliedRewardValue = applyMonthlyLimit(rule, rawRewardValue);

        if (isPositive(rawRewardValue) && isZero(appliedRewardValue)) {
            return reject(rule, BenefitRejectionReason.MONTHLY_LIMIT_EXHAUSTED);
        }

        return new BenefitCalculationResult(rule.ruleId(), rule.benefitType(), rule.rewardUnit(), true,
                rawRewardValue, appliedRewardValue, remainingLimitAfter(rule, appliedRewardValue),
                BenefitRejectionReason.NONE);
    }

    private BigDecimal calculateRawReward(BenefitRule rule, BenefitCalculationContext context) {
        return switch (rule.benefitBasis()) {
            case RATE -> multiplyAndFloor(benefitBaseAmount(rule, context.paymentAmount()), rule.rewardRate());
            case FIXED -> rule.rewardValue();
            // 12,900원에 1,000원당 1포인트라면 12포인트만 적립한다.
            case PER_SPEND_UNIT -> isZeroOrNegative(rule.spendUnitAmount())
                    ? BigDecimal.ZERO
                    : multiplyAndFloor(context.paymentAmount().divideToIntegralValue(rule.spendUnitAmount()),
                            rule.rewardValue());
            case PER_USAGE_UNIT -> multiplyAndFloor(benefitUsageQuantity(rule, context), rule.rewardValue());
        };
    }

    private BigDecimal benefitUsageQuantity(BenefitRule rule, BenefitCalculationContext context) {
        if (isZeroOrNegative(rule.maximumBenefitBaseAmount()) || isZeroOrNegative(context.paymentAmount())) {
            return context.usageQuantity();
        }
        BigDecimal recognizedPaymentAmount = benefitBaseAmount(rule, context.paymentAmount());
        return context.usageQuantity()
                .multiply(recognizedPaymentAmount)
                .divide(context.paymentAmount(), 10, RoundingMode.DOWN);
    }

    private BigDecimal benefitBaseAmount(BenefitRule rule, BigDecimal paymentAmount) {
        if (isZeroOrNegative(rule.maximumBenefitBaseAmount())) {
            return paymentAmount;
        }
        return min(paymentAmount, rule.maximumBenefitBaseAmount());
    }

    private BigDecimal multiplyAndFloor(BigDecimal value, BigDecimal multiplier) {
        // 카드 혜택은 원/포인트/마일 미만 단위를 올림하지 않고 절사한다.
        return value.multiply(multiplier)
                .setScale(0, RoundingMode.DOWN);
    }

    private BigDecimal capPaymentAmount(BenefitRule rule, BigDecimal rawRewardValue, BigDecimal paymentAmount) {
        // 원화 할인은 결제금액보다 커질 수 없지만, 캐시백/포인트/마일리지는 각 정책 단위를 따른다.
        if (rule.rewardUnit() == RewardUnit.KRW && rule.benefitType() == BenefitType.DISCOUNT) {
            return min(rawRewardValue, paymentAmount);
        }
        return rawRewardValue;
    }

    private BigDecimal applyMonthlyLimit(BenefitRule rule, BigDecimal rawRewardValue) {
        // 월 한도가 없는 룰은 계산된 혜택을 그대로 적용한다.
        if (isZeroOrNegative(rule.monthlyLimitValue())) {
            return rawRewardValue;
        }
        BigDecimal remainingLimit = max(rule.monthlyLimitValue().subtract(rule.usedMonthlyValue()), BigDecimal.ZERO);
        return min(rawRewardValue, remainingLimit);
    }

    private BigDecimal remainingLimitAfter(BenefitRule rule, BigDecimal appliedRewardValue) {
        if (isZeroOrNegative(rule.monthlyLimitValue())) {
            return BigDecimal.ZERO;
        }
        return max(rule.monthlyLimitValue().subtract(rule.usedMonthlyValue()).subtract(appliedRewardValue),
                BigDecimal.ZERO);
    }

    private BenefitCalculationResult reject(BenefitRule rule, BenefitRejectionReason reason) {
        return new BenefitCalculationResult(rule.ruleId(), rule.benefitType(), rule.rewardUnit(), false,
                BigDecimal.ZERO, BigDecimal.ZERO, remainingLimitAfter(rule, BigDecimal.ZERO), reason);
    }

    private boolean isLessThan(BigDecimal value, BigDecimal other) {
        return value.compareTo(other) < 0;
    }

    private boolean isPositive(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) > 0;
    }

    private boolean isZero(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) == 0;
    }

    private boolean isZeroOrNegative(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) <= 0;
    }

    private BigDecimal min(BigDecimal value, BigDecimal other) {
        return value.compareTo(other) <= 0 ? value : other;
    }

    private BigDecimal max(BigDecimal value, BigDecimal other) {
        return value.compareTo(other) >= 0 ? value : other;
    }
}
