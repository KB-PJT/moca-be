package com.moca.mocabe.domain.benefit.rule;

import com.moca.mocabe.domain.benefit.model.BenefitCalculationContext;
import java.util.Locale;
import java.util.Set;

/** 국내 거래 여부처럼 참·거짓으로 확정할 수 있는 조건을 판정한다. */
public class BooleanRuleConditionEvaluator implements RuleConditionEvaluator {
  private static final Set<String> TYPES =
      Set.of(
          "FOREIGN_TRANSACTION",
          "NEW_MEMBER_GRACE",
          "MERCHANT_ELIGIBLE",
          "PAYMENT_CHANNEL_ELIGIBLE");

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
    boolean expected = Boolean.parseBoolean(condition.value());
    boolean actual = switch (type) {
      case "FOREIGN_TRANSACTION" -> context.foreignTransaction();
      case "NEW_MEMBER_GRACE" -> context.newMemberGracePeriod();
      case "MERCHANT_ELIGIBLE" -> context.merchantEligible();
      case "PAYMENT_CHANNEL_ELIGIBLE" -> context.paymentChannelEligible();
      default -> false;
    };
    return actual == expected
        ? RuleConditionResult.matched()
        : RuleConditionResult.notMatched(RuleEvaluationSupport.rejectionReason(condition));
  }

  private String normalized(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }
}
