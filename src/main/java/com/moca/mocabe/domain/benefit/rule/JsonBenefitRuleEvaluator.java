package com.moca.mocabe.domain.benefit.rule;

import com.moca.mocabe.domain.benefit.calculation.BasicBenefitCalculator;
import com.moca.mocabe.domain.benefit.calculation.BenefitCalculator;
import com.moca.mocabe.domain.benefit.model.BenefitCalculationContext;
import com.moca.mocabe.domain.benefit.model.BenefitCalculationResult;
import com.moca.mocabe.domain.benefit.model.BenefitRule;
import com.moca.mocabe.domain.benefit.type.BenefitBasis;
import com.moca.mocabe.domain.benefit.type.BenefitPromotionCondition;
import com.moca.mocabe.domain.benefit.type.BenefitRejectionReason;
import com.moca.mocabe.domain.benefit.type.BenefitType;
import com.moca.mocabe.domain.benefit.type.RewardUnit;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** JSON 조건을 먼저 평가하고 검증된 산식만 기존 계산기에 전달한다. */
public class JsonBenefitRuleEvaluator {
  private final BenefitCalculator calculator;
  private final Map<String, RuleConditionEvaluator> evaluators;

  public JsonBenefitRuleEvaluator() {
    this(
        new BasicBenefitCalculator(),
        List.of(
            new NumericRuleConditionEvaluator(),
            new TargetRuleConditionEvaluator(),
            new TemporalRuleConditionEvaluator(),
            new BooleanRuleConditionEvaluator()));
  }

  JsonBenefitRuleEvaluator(
      BenefitCalculator calculator,
      List<RuleConditionEvaluator> conditionEvaluators) {
    this.calculator = calculator;
    Map<String, RuleConditionEvaluator> registered = new LinkedHashMap<>();
    for (RuleConditionEvaluator evaluator : conditionEvaluators) {
      for (String type : supportedTypes()) {
        if (evaluator.supports(type)) {
          registered.put(type, evaluator);
        }
      }
    }
    this.evaluators = Map.copyOf(registered);
  }

  public BenefitCalculationResult evaluate(
      String ruleId,
      BenefitRuleDefinition definition,
      BenefitCalculationContext context,
      BigDecimal monthlyLimitValue,
      BigDecimal usedMonthlyValue) {
    BenefitRule rule = toRule(ruleId, definition, monthlyLimitValue, usedMonthlyValue);
    RuleConditionResult conditionResult = evaluateConditions(definition.conditions(), context);
    if (conditionResult.decision() != RuleConditionDecision.MATCHED) {
      return rejected(rule, conditionResult.rejectionReason());
    }
    return calculator.calculate(rule, context);
  }

  private RuleConditionResult evaluateConditions(
      BenefitRuleDefinition.ConditionSet conditions,
      BenefitCalculationContext context) {
    RuleConditionResult all = evaluateAll(conditions.all(), context);
    if (all.decision() != RuleConditionDecision.MATCHED) {
      return all;
    }
    RuleConditionResult any = evaluateAny(conditions.any(), context);
    if (any.decision() != RuleConditionDecision.MATCHED) {
      return any;
    }
    return evaluateNone(conditions.none(), context);
  }

  private RuleConditionResult evaluateAll(
      List<BenefitRuleDefinition.Condition> conditions,
      BenefitCalculationContext context) {
    RuleConditionResult unavailable = null;
    for (BenefitRuleDefinition.Condition condition : conditions) {
      RuleConditionResult result = evaluate(condition, context);
      if (result.decision() == RuleConditionDecision.NOT_MATCHED) {
        return result;
      }
      if (result.decision() == RuleConditionDecision.UNAVAILABLE) {
        unavailable = result;
      }
    }
    return unavailable == null ? RuleConditionResult.matched() : unavailable;
  }

  private RuleConditionResult evaluateAny(
      List<BenefitRuleDefinition.Condition> conditions,
      BenefitCalculationContext context) {
    if (conditions.isEmpty()) {
      return RuleConditionResult.matched();
    }
    RuleConditionResult unavailable = null;
    RuleConditionResult notMatched = null;
    for (BenefitRuleDefinition.Condition condition : conditions) {
      RuleConditionResult result = evaluate(condition, context);
      if (result.decision() == RuleConditionDecision.MATCHED) {
        return result;
      }
      if (result.decision() == RuleConditionDecision.UNAVAILABLE) {
        unavailable = result;
      } else {
        notMatched = result;
      }
    }
    return unavailable != null ? unavailable : notMatched;
  }

  private RuleConditionResult evaluateNone(
      List<BenefitRuleDefinition.Condition> conditions,
      BenefitCalculationContext context) {
    RuleConditionResult unavailable = null;
    for (BenefitRuleDefinition.Condition condition : conditions) {
      RuleConditionResult result = evaluate(condition, context);
      if (result.decision() == RuleConditionDecision.MATCHED) {
        return RuleConditionResult.notMatched(RuleEvaluationSupport.rejectionReason(condition));
      }
      if (result.decision() == RuleConditionDecision.UNAVAILABLE) {
        unavailable = result;
      }
    }
    return unavailable == null ? RuleConditionResult.matched() : unavailable;
  }

  private RuleConditionResult evaluate(
      BenefitRuleDefinition.Condition condition,
      BenefitCalculationContext context) {
    RuleConditionEvaluator evaluator = evaluators.get(normalized(condition.type()));
    return evaluator == null
        ? RuleConditionResult.unavailable()
        : evaluator.evaluate(condition, context);
  }

  private BenefitRule toRule(
      String ruleId,
      BenefitRuleDefinition definition,
      BigDecimal monthlyLimitValue,
      BigDecimal usedMonthlyValue) {
    BenefitRuleDefinition.Reward reward = definition.reward();
    BenefitBasis basis = BenefitBasis.valueOf(normalized(reward.calculation()));
    RewardUnit unit = RewardUnit.valueOf(normalized(reward.rewardUnit()));
    return new BenefitRule(
        ruleId,
        BenefitType.valueOf(normalized(reward.benefitType())),
        basis,
        unit,
        decimal(reward.rate()),
        decimal(reward.value()),
        decimal(reward.spendUnitAmount()),
        limit(definition, "TRANSACTION_BENEFIT_BASE"),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        zero(monthlyLimitValue),
        zero(usedMonthlyValue),
        BenefitPromotionCondition.NONE,
        Set.of(),
        integerLimit(definition, "DAILY_USAGE_COUNT"),
        integerLimit(definition, "MONTHLY_USAGE_COUNT"),
        false,
        false,
        Set.of(),
        Set.of());
  }

  private BenefitCalculationResult rejected(BenefitRule rule, BenefitRejectionReason reason) {
    return new BenefitCalculationResult(
        rule.ruleId(),
        rule.benefitType(),
        rule.rewardUnit(),
        false,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        rule.monthlyLimitValue().subtract(rule.usedMonthlyValue()).max(BigDecimal.ZERO),
        reason);
  }

  private BigDecimal limit(BenefitRuleDefinition definition, String type) {
    return definition.limits().stream()
        .filter(limit -> type.equals(normalized(limit.type())))
        .map(BenefitRuleDefinition.Limit::value)
        .map(this::decimal)
        .findFirst()
        .orElse(BigDecimal.ZERO);
  }

  private int integerLimit(BenefitRuleDefinition definition, String type) {
    return limit(definition, type).intValueExact();
  }

  private BigDecimal decimal(String value) {
    return value == null || value.isBlank() ? BigDecimal.ZERO : new BigDecimal(value);
  }

  private BigDecimal zero(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private String normalized(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  private Set<String> supportedTypes() {
    return Set.of(
        "PAYMENT_AMOUNT",
        "PREVIOUS_MONTH_SPEND",
        "USED_DAILY_COUNT",
        "USED_MONTHLY_COUNT",
        "MERCHANT",
        "MERCHANT_CATEGORY",
        "DAY_OF_WEEK",
        "APPROVED_TIME",
        "FOREIGN_TRANSACTION",
        "NEW_MEMBER_GRACE",
        "MERCHANT_ELIGIBLE",
        "PAYMENT_CHANNEL_ELIGIBLE");
  }
}
