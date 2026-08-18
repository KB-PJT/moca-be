package com.moca.mocabe.domain.benefit.rule;

import com.moca.mocabe.domain.benefit.model.BenefitCalculationContext;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

/** 승인금액·실적·사용 횟수처럼 CODEF와 내부 원장에서 확인 가능한 숫자 조건을 판정한다. */
public class NumericRuleConditionEvaluator implements RuleConditionEvaluator {
  private static final Set<String> TYPES =
      Set.of("PAYMENT_AMOUNT", "PREVIOUS_MONTH_SPEND", "USED_DAILY_COUNT", "USED_MONTHLY_COUNT");

  @Override
  public boolean supports(String conditionType) {
    return TYPES.contains(normalized(conditionType));
  }

  @Override
  public RuleConditionResult evaluate(
      BenefitRuleDefinition.Condition condition,
      BenefitCalculationContext context) {
    String type = normalized(condition.type());
    if (!context.hasTarget("AVAILABLE_FIELD", type)) {
      return RuleConditionResult.unavailable();
    }
    BigDecimal actual = actual(type, context);
    BigDecimal expected;
    try {
      expected = new BigDecimal(condition.value());
    } catch (RuntimeException exception) {
      return RuleConditionResult.unavailable();
    }
    boolean matched = switch (normalized(condition.operator())) {
      case "GT" -> actual.compareTo(expected) > 0;
      case "GTE" -> actual.compareTo(expected) >= 0;
      case "LT" -> actual.compareTo(expected) < 0;
      case "LTE" -> actual.compareTo(expected) <= 0;
      case "EQ" -> actual.compareTo(expected) == 0;
      default -> false;
    };
    return matched
        ? RuleConditionResult.matched()
        : RuleConditionResult.notMatched(RuleEvaluationSupport.rejectionReason(condition));
  }

  private BigDecimal actual(String type, BenefitCalculationContext context) {
    return switch (type) {
      case "PAYMENT_AMOUNT" -> context.paymentAmount();
      case "PREVIOUS_MONTH_SPEND" -> context.previousMonthSpend();
      case "USED_DAILY_COUNT" -> BigDecimal.valueOf(context.usedDailyCount());
      case "USED_MONTHLY_COUNT" -> BigDecimal.valueOf(context.usedMonthlyCount());
      default -> BigDecimal.ZERO;
    };
  }

  private String normalized(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }
}
