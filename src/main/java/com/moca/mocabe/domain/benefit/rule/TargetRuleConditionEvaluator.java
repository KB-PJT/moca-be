package com.moca.mocabe.domain.benefit.rule;

import com.moca.mocabe.domain.benefit.model.BenefitCalculationContext;
import java.util.Locale;
import java.util.Set;

/** 가맹점과 내부 카테고리 계층 조건을 판정한다. */
public class TargetRuleConditionEvaluator implements RuleConditionEvaluator {
  private static final Set<String> TYPES =
      Set.of("MERCHANT", "MERCHANT_CATEGORY", "TRANSACTION_TYPE");

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
    String targetType = switch (type) {
      case "MERCHANT" -> "MERCHANT";
      case "TRANSACTION_TYPE" -> "TRANSACTION_TYPE";
      default -> "MERCHANT_CATEGORY_CODE";
    };
    boolean matched = switch (normalized(condition.operator())) {
      case "IN" -> condition.values().stream().anyMatch(value -> context.hasTarget(targetType, value));
      case "EQ" -> context.hasTarget(targetType, condition.value());
      default -> false;
    };
    return matched
        ? RuleConditionResult.matched()
        : RuleConditionResult.notMatched(RuleEvaluationSupport.rejectionReason(condition));
  }

  private String normalized(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }
}
