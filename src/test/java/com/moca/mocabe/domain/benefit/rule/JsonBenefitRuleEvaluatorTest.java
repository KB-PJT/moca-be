package com.moca.mocabe.domain.benefit.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moca.mocabe.domain.benefit.calculation.BasicBenefitCalculator;
import com.moca.mocabe.domain.benefit.model.BenefitCalculationContext;
import com.moca.mocabe.domain.benefit.model.BenefitCalculationResult;
import com.moca.mocabe.domain.benefit.type.BenefitRejectionReason;
import com.moca.mocabe.domain.benefit.type.BenefitType;
import com.moca.mocabe.domain.benefit.type.RewardUnit;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JsonBenefitRuleEvaluatorTest {
  private final JsonBenefitRuleEvaluator evaluator = new JsonBenefitRuleEvaluator();

  @Test
  void appliesRateRewardWithTransactionAndFrequencyLimits() {
    BenefitRuleDefinition definition =
        definition(
            List.of(condition("PREVIOUS_MONTH_SPEND", "GTE", "300000")),
            List.of(),
            List.of(),
            reward("DISCOUNT", "KRW", "RATE", "0.1", null, null),
            List.of(
                new BenefitRuleDefinition.Limit("TRANSACTION_BENEFIT_BASE", "5000"),
                new BenefitRuleDefinition.Limit("DAILY_USAGE_COUNT", "2"),
                new BenefitRuleDefinition.Limit("MONTHLY_USAGE_COUNT", "10")));

    BenefitCalculationResult result =
        evaluator.evaluate("rule", definition, context(allAvailable(), 1, 3),
            new BigDecimal("1000"), new BigDecimal("200"));

    assertTrue(result.applicable());
    assertEquals(BenefitType.DISCOUNT, result.benefitType());
    assertEquals(RewardUnit.KRW, result.rewardUnit());
    assertEquals(new BigDecimal("500"), result.appliedRewardValue());
    assertEquals(new BigDecimal("300"), result.remainingLimitValue());
  }

  @Test
  void appliesFixedPerSpendAndPerUsageRewards() {
    BenefitCalculationResult fixed = evaluate(reward("CASHBACK", "KRW", "FIXED", null, "700", null));
    BenefitCalculationResult perSpend =
        evaluate(reward("POINT", "POINT", "PER_SPEND_UNIT", null, "1", "1000"));
    BenefitCalculationResult perUsage =
        evaluate(reward("MILEAGE", "MILE", "PER_USAGE_UNIT", null, "2", null));

    assertEquals(new BigDecimal("700"), fixed.appliedRewardValue());
    assertEquals(new BigDecimal("10"), perSpend.appliedRewardValue());
    assertEquals(new BigDecimal("2"), perUsage.appliedRewardValue());
  }

  @Test
  void appliesAnyAndNoneLikeIfElseConditions() {
    BenefitRuleDefinition anyMatched =
        definition(
            List.of(),
            List.of(
                condition("NEW_MEMBER_GRACE", "EQ", "true"),
                condition("PREVIOUS_MONTH_SPEND", "GTE", "300000")),
            List.of(condition("FOREIGN_TRANSACTION", "EQ", "true")),
            reward("DISCOUNT", "KRW", "RATE", "0.1", null, null),
            List.of());

    BenefitCalculationResult result =
        evaluator.evaluate("rule", anyMatched, context(allAvailable(), 0, 0),
            BigDecimal.ZERO, BigDecimal.ZERO);
    assertTrue(result.applicable());

    BenefitRuleDefinition excluded =
        definition(
            List.of(),
            List.of(),
            List.of(condition("FOREIGN_TRANSACTION", "EQ", "false")),
            anyMatched.reward(),
            List.of());
    BenefitCalculationResult excludedResult =
        evaluator.evaluate("rule", excluded, context(allAvailable(), 0, 0),
            BigDecimal.ZERO, BigDecimal.ZERO);
    assertFalse(excludedResult.applicable());
    assertEquals(BenefitRejectionReason.PERFORMANCE_NOT_MET, excludedResult.rejectionReason());
  }

  @Test
  void failsClosedWhenAllAnyNoneOrTypeCannotBeEvaluated() {
    Set<String> onlyPayment = Set.of("PAYMENT_AMOUNT");
    BenefitRuleDefinition unavailableAll =
        definition(
            List.of(condition("PREVIOUS_MONTH_SPEND", "GTE", "300000")),
            List.of(),
            List.of(),
            reward("DISCOUNT", "KRW", "RATE", "0.1", null, null),
            List.of());
    BenefitRuleDefinition unavailableAny =
        definition(
            List.of(),
            List.of(
                condition("PREVIOUS_MONTH_SPEND", "GTE", "300000"),
                condition("PAYMENT_AMOUNT", "GT", "99999")),
            List.of(),
            unavailableAll.reward(),
            List.of());
    BenefitRuleDefinition unavailableNone =
        definition(
            List.of(),
            List.of(),
            List.of(condition("PREVIOUS_MONTH_SPEND", "GTE", "300000")),
            unavailableAll.reward(),
            List.of());
    BenefitRuleDefinition unknown =
        definition(
            List.of(new BenefitRuleDefinition.Condition("UNKNOWN", "EQ", "1", null, null)),
            List.of(),
            List.of(),
            unavailableAll.reward(),
            List.of());

    assertUnavailable(evaluator.evaluate("all", unavailableAll, context(onlyPayment, 0, 0),
        new BigDecimal("100"), new BigDecimal("30")));
    assertUnavailable(evaluator.evaluate("any", unavailableAny, context(onlyPayment, 0, 0),
        BigDecimal.ZERO, BigDecimal.ZERO));
    assertUnavailable(evaluator.evaluate("none", unavailableNone, context(onlyPayment, 0, 0),
        BigDecimal.ZERO, BigDecimal.ZERO));
    assertUnavailable(evaluator.evaluate("unknown", unknown, context(onlyPayment, 0, 0),
        BigDecimal.ZERO, BigDecimal.ZERO));

    JsonBenefitRuleEvaluator noConditions =
        new JsonBenefitRuleEvaluator(new BasicBenefitCalculator(), List.of());
    assertUnavailable(noConditions.evaluate("empty", unknown, context(onlyPayment, 0, 0),
        BigDecimal.ZERO, BigDecimal.ZERO));
  }

  @Test
  void returnsConfiguredReasonWhenAllOrAnyDoesNotMatch() {
    BenefitRuleDefinition allRejected =
        definition(
            List.of(condition("PAYMENT_AMOUNT", "GTE", "20000")),
            List.of(),
            List.of(),
            reward("DISCOUNT", "KRW", "RATE", "0.1", null, null),
            List.of());
    BenefitRuleDefinition anyRejected =
        definition(
            List.of(),
            List.of(
                condition("PAYMENT_AMOUNT", "GT", "20000"),
                condition("USED_DAILY_COUNT", "GT", "10")),
            List.of(),
            allRejected.reward(),
            List.of());

    assertEquals(
        BenefitRejectionReason.PERFORMANCE_NOT_MET,
        evaluator.evaluate("all", allRejected, context(allAvailable(), 0, 0),
            BigDecimal.ZERO, BigDecimal.ZERO).rejectionReason());
    assertEquals(
        BenefitRejectionReason.PERFORMANCE_NOT_MET,
        evaluator.evaluate("any", anyRejected, context(allAvailable(), 0, 0),
            BigDecimal.ZERO, BigDecimal.ZERO).rejectionReason());
  }

  @Test
  void frequencyLimitRejectsAfterConditionMatch() {
    BenefitRuleDefinition definition =
        definition(
            List.of(),
            List.of(),
            List.of(),
            reward("DISCOUNT", "KRW", "RATE", "0.1", null, null),
            List.of(new BenefitRuleDefinition.Limit("DAILY_USAGE_COUNT", "1")));

    BenefitCalculationResult result =
        evaluator.evaluate("rule", definition, context(allAvailable(), 1, 1),
            BigDecimal.ZERO, BigDecimal.ZERO);

    assertFalse(result.applicable());
    assertEquals(BenefitRejectionReason.FREQUENCY_LIMIT_EXHAUSTED, result.rejectionReason());
  }

  private BenefitCalculationResult evaluate(BenefitRuleDefinition.Reward reward) {
    return evaluator.evaluate(
        "rule",
        definition(List.of(), List.of(), List.of(), reward, List.of()),
        context(allAvailable(), 0, 0),
        BigDecimal.ZERO,
        BigDecimal.ZERO);
  }

  private void assertUnavailable(BenefitCalculationResult result) {
    assertFalse(result.applicable());
    assertEquals(BenefitRejectionReason.RULE_DATA_UNAVAILABLE, result.rejectionReason());
  }

  private BenefitRuleDefinition definition(
      List<BenefitRuleDefinition.Condition> all,
      List<BenefitRuleDefinition.Condition> any,
      List<BenefitRuleDefinition.Condition> none,
      BenefitRuleDefinition.Reward reward,
      List<BenefitRuleDefinition.Limit> limits) {
    return new BenefitRuleDefinition(
        1, new BenefitRuleDefinition.ConditionSet(all, any, none), reward, limits);
  }

  private BenefitRuleDefinition.Reward reward(
      String type,
      String unit,
      String calculation,
      String rate,
      String value,
      String spendUnit) {
    return new BenefitRuleDefinition.Reward(type, unit, calculation, rate, value, spendUnit);
  }

  private BenefitRuleDefinition.Condition condition(String type, String operator, String value) {
    return new BenefitRuleDefinition.Condition(
        type, operator, value, List.of(), "PERFORMANCE_NOT_MET");
  }

  private BenefitCalculationContext context(Set<String> available, int daily, int monthly) {
    return new BenefitCalculationContext(
        new BigDecimal("10000"),
        BigDecimal.ONE,
        new BigDecimal("500000"),
        LocalDateTime.of(2026, 8, 14, 3, 0),
        "CAFE",
        false,
        daily,
        monthly,
        true,
        true,
        false,
        Map.of("AVAILABLE_FIELD", available));
  }

  private Set<String> allAvailable() {
    return Set.of(
        "PAYMENT_AMOUNT",
        "PREVIOUS_MONTH_SPEND",
        "USED_DAILY_COUNT",
        "USED_MONTHLY_COUNT",
        "APPROVED_AT",
        "FOREIGN_TRANSACTION");
  }
}
