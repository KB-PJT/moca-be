package com.moca.mocabe.domain.benefit.calculation;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;

import com.moca.mocabe.domain.benefit.model.BenefitCalculationContext;
import com.moca.mocabe.domain.benefit.model.BenefitCalculationResult;
import com.moca.mocabe.domain.benefit.model.BenefitRule;
import com.moca.mocabe.domain.benefit.type.BenefitRejectionReason;

/**
 * Night, Weekend처럼 시간성 조건이 붙는 혜택을 먼저 검증한 뒤 금액 계산을 위임한다.
 */
public class PromotionBenefitCalculator implements BenefitCalculator {

    private static final LocalTime NIGHT_START_TIME = LocalTime.of(21, 0);
    private static final LocalTime NIGHT_END_TIME = LocalTime.of(9, 0);

    private final BenefitCalculator delegate;

    public PromotionBenefitCalculator() {
        this(new BasicBenefitCalculator());
    }

    public PromotionBenefitCalculator(BenefitCalculator delegate) {
        this.delegate = delegate;
    }

    @Override
    public BenefitCalculationResult calculate(BenefitRule rule, BenefitCalculationContext context) {
        if (context.foreignTransaction()) {
            return new BenefitCalculationResult(rule.ruleId(), rule.benefitType(), rule.rewardUnit(), false,
                    BigDecimal.ZERO, BigDecimal.ZERO, remainingLimit(rule),
                    BenefitRejectionReason.FOREIGN_TRANSACTION_NOT_SUPPORTED);
        }
        if (!matchesPromotionCondition(rule, context)) {
            return new BenefitCalculationResult(rule.ruleId(), rule.benefitType(), rule.rewardUnit(), false,
                    BigDecimal.ZERO, BigDecimal.ZERO, remainingLimit(rule), BenefitRejectionReason.CONDITION_NOT_MET);
        }
        return delegate.calculate(rule, context);
    }

    private boolean matchesPromotionCondition(BenefitRule rule, BenefitCalculationContext context) {
        return switch (rule.promotionCondition()) {
            case NONE -> true;
            case NIGHT_TIME -> context.approvedAt() != null && isNightTime(context.approvedAt().toLocalTime());
            case WEEKEND -> context.approvedAt() != null && isWeekend(context.approvedAt().getDayOfWeek());
        };
    }

    private boolean isNightTime(LocalTime approvedTime) {
        return !approvedTime.isBefore(NIGHT_START_TIME) || approvedTime.isBefore(NIGHT_END_TIME);
    }

    private boolean isWeekend(DayOfWeek dayOfWeek) {
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    }

    private BigDecimal remainingLimit(BenefitRule rule) {
        if (rule.monthlyLimitValue().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal remainingLimit = rule.monthlyLimitValue().subtract(rule.usedMonthlyValue());
        return remainingLimit.compareTo(BigDecimal.ZERO) >= 0 ? remainingLimit : BigDecimal.ZERO;
    }
}
