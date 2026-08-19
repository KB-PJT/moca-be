package com.moca.mocabe.domain.benefit.rule;

import com.moca.mocabe.domain.benefit.type.BenefitRejectionReason;

/** 조건 판정과 사용자에게 표시할 미적용 사유를 함께 보존한다. */
public record RuleConditionResult(
    RuleConditionDecision decision,
    BenefitRejectionReason rejectionReason) {

  public static RuleConditionResult matched() {
    return new RuleConditionResult(RuleConditionDecision.MATCHED, BenefitRejectionReason.NONE);
  }

  public static RuleConditionResult notMatched(BenefitRejectionReason reason) {
    return new RuleConditionResult(RuleConditionDecision.NOT_MATCHED, reason);
  }

  public static RuleConditionResult unavailable() {
    return new RuleConditionResult(
        RuleConditionDecision.UNAVAILABLE, BenefitRejectionReason.RULE_DATA_UNAVAILABLE);
  }
}
