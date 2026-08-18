package com.moca.mocabe.domain.benefit.rule;

import com.moca.mocabe.domain.benefit.model.BenefitCalculationContext;

/** 한 종류의 JSON 조건을 CODEF 기반 계산 문맥으로 판정한다. */
public interface RuleConditionEvaluator {
  boolean supports(String conditionType);

  RuleConditionResult evaluate(
      BenefitRuleDefinition.Condition condition,
      BenefitCalculationContext context);
}
