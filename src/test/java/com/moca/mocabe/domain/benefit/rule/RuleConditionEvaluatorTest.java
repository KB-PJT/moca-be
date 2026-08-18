package com.moca.mocabe.domain.benefit.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moca.mocabe.domain.benefit.model.BenefitCalculationContext;
import com.moca.mocabe.domain.benefit.type.BenefitRejectionReason;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RuleConditionEvaluatorTest {

  @Test
  void evaluatesEveryNumericOperatorAndSource() {
    NumericRuleConditionEvaluator evaluator = new NumericRuleConditionEvaluator();
    BenefitCalculationContext context = context(allAvailable());

    assertTrue(evaluator.supports("payment_amount"));
    assertFalse(evaluator.supports("merchant"));
    assertMatched(evaluator.evaluate(condition("PAYMENT_AMOUNT", "GT", "9999"), context));
    assertMatched(evaluator.evaluate(condition("PREVIOUS_MONTH_SPEND", "GTE", "500000"), context));
    assertMatched(evaluator.evaluate(condition("USED_DAILY_COUNT", "LT", "2"), context));
    assertMatched(evaluator.evaluate(condition("USED_MONTHLY_COUNT", "LTE", "3"), context));
    assertMatched(evaluator.evaluate(condition("PAYMENT_AMOUNT", "EQ", "10000"), context));
    assertNotMatched(evaluator.evaluate(condition("PAYMENT_AMOUNT", "GT", "10000"), context));
    assertNotMatched(evaluator.evaluate(condition("PAYMENT_AMOUNT", "UNKNOWN", "10000"), context));
    assertUnavailable(evaluator.evaluate(condition("PAYMENT_AMOUNT", "EQ", "bad"), context));
    assertNotMatched(
        evaluator.evaluate(condition("UNKNOWN", "EQ", "1"), context(Set.of("UNKNOWN"))));
    assertUnavailable(
        evaluator.evaluate(
            condition("PREVIOUS_MONTH_SPEND", "EQ", "500000"),
            context(Set.of("PAYMENT_AMOUNT"))));
  }

  @Test
  void evaluatesMerchantAndCategoryTargets() {
    TargetRuleConditionEvaluator evaluator = new TargetRuleConditionEvaluator();
    BenefitCalculationContext context = context(allAvailable());

    assertTrue(evaluator.supports("merchant"));
    assertFalse(evaluator.supports("APPROVED_TIME"));
    assertMatched(evaluator.evaluate(condition("MERCHANT", "EQ", "merchant-id"), context));
    assertMatched(
        evaluator.evaluate(
            valuesCondition("MERCHANT_CATEGORY", "IN", List.of("CAFE", "BOOKS")), context));
    assertNotMatched(evaluator.evaluate(condition("MERCHANT", "UNKNOWN", "merchant-id"), context));
    assertNotMatched(evaluator.evaluate(condition("MERCHANT", "EQ", "other"), context));
    assertUnavailable(
        evaluator.evaluate(condition("MERCHANT", "EQ", "merchant-id"), context(Set.of())));
  }

  @Test
  void evaluatesSeoulDayAndNormalOrCrossMidnightTimeRanges() {
    TemporalRuleConditionEvaluator evaluator = new TemporalRuleConditionEvaluator();
    BenefitCalculationContext noon = context(allAvailable());
    BenefitCalculationContext midnight =
        new BenefitCalculationContext(
            BigDecimal.TEN,
            BigDecimal.ONE,
            BigDecimal.ZERO,
            LocalDateTime.of(2026, 8, 14, 15, 30),
            "CAFE",
            false,
            0,
            0,
            true,
            true,
            false,
            Map.of("AVAILABLE_FIELD", Set.of("APPROVED_AT")));

    assertTrue(evaluator.supports("day_of_week"));
    assertFalse(evaluator.supports("PAYMENT_AMOUNT"));
    assertMatched(
        evaluator.evaluate(valuesCondition("DAY_OF_WEEK", "IN", List.of("FRIDAY")), noon));
    assertMatched(
        evaluator.evaluate(
            valuesCondition("APPROVED_TIME", "BETWEEN", List.of("09:00", "18:00")), noon));
    assertMatched(
        evaluator.evaluate(
            valuesCondition("APPROVED_TIME", "BETWEEN", List.of("23:00", "02:00")),
            midnight));
    assertNotMatched(
        evaluator.evaluate(
            valuesCondition("APPROVED_TIME", "BETWEEN", List.of("18:00", "19:00")), noon));
    assertNotMatched(
        evaluator.evaluate(valuesCondition("APPROVED_TIME", "BETWEEN", List.of("bad")), noon));
    assertNotMatched(
        evaluator.evaluate(
            valuesCondition("APPROVED_TIME", "BETWEEN", List.of("bad", "19:00")), noon));
    assertUnavailable(
        evaluator.evaluate(
            valuesCondition("DAY_OF_WEEK", "IN", List.of("FRIDAY")), context(Set.of())));
  }

  @Test
  void evaluatesEveryBooleanSourceAndUnavailableField() {
    BooleanRuleConditionEvaluator evaluator = new BooleanRuleConditionEvaluator();
    BenefitCalculationContext context = context(allAvailable());

    assertTrue(evaluator.supports("foreign_transaction"));
    assertFalse(evaluator.supports("PAYMENT_AMOUNT"));
    assertMatched(evaluator.evaluate(condition("FOREIGN_TRANSACTION", "EQ", "false"), context));
    assertMatched(evaluator.evaluate(condition("NEW_MEMBER_GRACE", "EQ", "false"), context));
    assertMatched(evaluator.evaluate(condition("MERCHANT_ELIGIBLE", "EQ", "true"), context));
    assertMatched(
        evaluator.evaluate(condition("PAYMENT_CHANNEL_ELIGIBLE", "EQ", "true"), context));
    assertNotMatched(evaluator.evaluate(condition("MERCHANT_ELIGIBLE", "EQ", "false"), context));
    assertUnavailable(
        evaluator.evaluate(condition("NEW_MEMBER_GRACE", "EQ", "false"), context(Set.of())));
  }

  @Test
  void resultFactoriesAndFallbackReasonAreStable() {
    assertEquals(RuleConditionDecision.MATCHED, RuleConditionResult.matched().decision());
    assertEquals(
        BenefitRejectionReason.PERFORMANCE_NOT_MET,
        RuleConditionResult.notMatched(BenefitRejectionReason.PERFORMANCE_NOT_MET)
            .rejectionReason());
    assertEquals(
        BenefitRejectionReason.RULE_DATA_UNAVAILABLE,
        RuleConditionResult.unavailable().rejectionReason());
    assertEquals(
        BenefitRejectionReason.CONDITION_NOT_MET,
        RuleEvaluationSupport.rejectionReason(
            new BenefitRuleDefinition.Condition("PAYMENT_AMOUNT", "EQ", "1", null, "bad")));
  }

  private BenefitCalculationContext context(Set<String> available) {
    return new BenefitCalculationContext(
        new BigDecimal("10000"),
        BigDecimal.ONE,
        new BigDecimal("500000"),
        LocalDateTime.of(2026, 8, 14, 3, 0),
        "CAFE",
        false,
        1,
        3,
        true,
        true,
        false,
        Map.of(
            "AVAILABLE_FIELD", available,
            "MERCHANT", Set.of("merchant-id"),
            "MERCHANT_CATEGORY_CODE", Set.of("CAFE")));
  }

  private Set<String> allAvailable() {
    return Set.of(
        "PAYMENT_AMOUNT",
        "PREVIOUS_MONTH_SPEND",
        "USED_DAILY_COUNT",
        "USED_MONTHLY_COUNT",
        "MERCHANT",
        "MERCHANT_CATEGORY",
        "APPROVED_AT",
        "FOREIGN_TRANSACTION",
        "NEW_MEMBER_GRACE",
        "MERCHANT_ELIGIBLE",
        "PAYMENT_CHANNEL_ELIGIBLE");
  }

  private BenefitRuleDefinition.Condition condition(String type, String operator, String value) {
    return new BenefitRuleDefinition.Condition(
        type, operator, value, List.of(), "PERFORMANCE_NOT_MET");
  }

  private BenefitRuleDefinition.Condition valuesCondition(
      String type, String operator, List<String> values) {
    return new BenefitRuleDefinition.Condition(
        type, operator, null, values, "TARGET_NOT_MATCHED");
  }

  private void assertMatched(RuleConditionResult result) {
    assertEquals(RuleConditionDecision.MATCHED, result.decision());
  }

  private void assertNotMatched(RuleConditionResult result) {
    assertEquals(RuleConditionDecision.NOT_MATCHED, result.decision());
  }

  private void assertUnavailable(RuleConditionResult result) {
    assertEquals(RuleConditionDecision.UNAVAILABLE, result.decision());
  }
}
