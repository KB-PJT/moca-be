package com.moca.mocabe.domain.benefit.rule;

import com.moca.mocabe.domain.benefit.type.BenefitRejectionReason;
import java.util.Locale;

final class RuleEvaluationSupport {
  private RuleEvaluationSupport() { }

  static BenefitRejectionReason rejectionReason(BenefitRuleDefinition.Condition condition) {
    try {
      return BenefitRejectionReason.valueOf(normalized(condition.rejectionReason()));
    } catch (IllegalArgumentException exception) {
      return BenefitRejectionReason.CONDITION_NOT_MET;
    }
  }

  private static String normalized(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }
}
