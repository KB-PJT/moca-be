package com.moca.mocabe.domain.benefit.structuring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moca.mocabe.domain.benefit.model.BenefitCalculationContext;
import com.moca.mocabe.domain.benefit.model.BenefitCalculationResult;
import com.moca.mocabe.domain.benefit.rule.BenefitRuleDefinition;
import com.moca.mocabe.domain.benefit.rule.BenefitRuleDefinitionParser;
import com.moca.mocabe.domain.benefit.rule.JsonBenefitRuleEvaluator;
import com.moca.mocabe.domain.benefit.type.BenefitRejectionReason;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@DisplayName("정규화 룰 JSON factory")
class NormalizedRuleJsonFactoryTest {
  private final NormalizedRuleJsonFactory factory = new NormalizedRuleJsonFactory();

  @Test
  @DisplayName("실적 tier, 거래 조건, 횟수·산정 상한과 시간 조건을 JSON 룰로 투영한다")
  void createsValidatedJsonRule() {
    NormalizedRule rule = rule();

    BenefitRuleDefinition definition = new BenefitRuleDefinitionParser().parse(factory.create(rule));

    assertEquals("0.05", definition.reward().rate());
    assertEquals(5, definition.conditions().all().size());
    assertEquals("MONTHLY_USAGE_COUNT", definition.limits().get(0).type());
    assertEquals("TRANSACTION_BENEFIT_BASE", definition.limits().get(1).type());
  }

  @Test
  @DisplayName("생성한 JSON 룰은 시간·횟수 조건과 거래당 산정 상한을 실제 계산에 적용한다")
  void evaluatesProjectedConditionsAndBenefitBaseCap() {
    BenefitRuleDefinition definition = new BenefitRuleDefinitionParser().parse(factory.create(rule()));
    JsonBenefitRuleEvaluator evaluator = new JsonBenefitRuleEvaluator();

    BenefitCalculationResult applied = evaluator.evaluate(
        "rule", definition, context(1, LocalDateTime.of(2026, 8, 14, 3, 0)), BigDecimal.ZERO,
        BigDecimal.ZERO);
    BenefitCalculationResult frequencyRejected = evaluator.evaluate(
        "rule", definition, context(2, LocalDateTime.of(2026, 8, 14, 3, 0)), BigDecimal.ZERO,
        BigDecimal.ZERO);
    BenefitCalculationResult scheduleRejected = evaluator.evaluate(
        "rule", definition, context(1, LocalDateTime.of(2026, 8, 15, 3, 0)), BigDecimal.ZERO,
        BigDecimal.ZERO);

    assertTrue(applied.applicable());
    assertEquals(new BigDecimal("2500"), applied.appliedRewardValue());
    assertFalse(frequencyRejected.applicable());
    assertEquals(BenefitRejectionReason.FREQUENCY_LIMIT_EXHAUSTED, frequencyRejected.rejectionReason());
    assertFalse(scheduleRejected.applicable());
    assertEquals(BenefitRejectionReason.CONDITION_NOT_MET, scheduleRejected.rejectionReason());
  }

  @Test
  @DisplayName("모든 보상·한도 분기와 nullable 정규화 모델을 투영한다")
  void projectsEveryRewardAndOptionalBranch() {
    assertReward(ParsedReward.Type.FIXED_KRW, "DISCOUNT", "KRW");
    assertReward(ParsedReward.Type.CASHBACK, "CASHBACK", "KRW");
    assertReward(ParsedReward.Type.POINT, "POINT", "POINT");
    assertReward(ParsedReward.Type.MILEAGE, "MILEAGE", "MILE");

    NormalizedRule defaults = new NormalizedRule("", null, null, null, null, null, null);
    assertTrue(defaults.reward().isEmpty());
    assertTrue(defaults.limits().isEmpty());
    assertTrue(new ParsedSchedule(null, null, null).days().isEmpty());
    assertThrows(IllegalArgumentException.class, () -> factory.create(defaults));

    NormalizedRule daily = new NormalizedRule("daily",
        Optional.of(new ParsedReward(ParsedReward.Type.POINT, BigDecimal.ONE, "1P")),
        Optional.empty(), Optional.of(new ParsedTransactionCondition(null, BigDecimal.TEN, null)),
        List.of(new ParsedLimit(ParsedLimit.Period.DAILY, ParsedLimit.Type.COUNT, BigDecimal.ONE),
            new ParsedLimit(ParsedLimit.Period.YEARLY, ParsedLimit.Type.COUNT, BigDecimal.ONE)),
        Optional.empty(), Optional.empty());
    BenefitRuleDefinition definition = new BenefitRuleDefinitionParser().parse(factory.create(daily));
    assertEquals("DAILY_USAGE_COUNT", definition.limits().get(0).type());
    assertEquals("LTE", definition.conditions().all().get(0).operator());
  }

  @Test
  @DisplayName("JSON 직렬화 실패를 도메인 예외로 변환한다")
  void wrapsSerializationFailure() throws Exception {
    ObjectMapper objectMapper = Mockito.mock(ObjectMapper.class);
    when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("failure") { });

    NormalizedRuleJsonFactory failing = new NormalizedRuleJsonFactory(objectMapper);

    assertThrows(IllegalStateException.class, () -> failing.create(rule()));
  }

  private void assertReward(ParsedReward.Type type, String benefitType, String unit) {
    NormalizedRule candidate = new NormalizedRule("reward",
        Optional.of(new ParsedReward(type, BigDecimal.TEN, "reward")), Optional.empty(),
        Optional.empty(), List.of(), Optional.empty(), Optional.empty());
    BenefitRuleDefinition definition = new BenefitRuleDefinitionParser().parse(factory.create(candidate));
    assertEquals(benefitType, definition.reward().benefitType());
    assertEquals(unit, definition.reward().rewardUnit());
  }

  private NormalizedRule rule() {
    return new NormalizedRule(
        "전월 실적 30만원 이상 70만원 미만, 건당 1만원 이상 5% 할인, 월 2회",
        Optional.of(new ParsedReward(ParsedReward.Type.PERCENT, new BigDecimal("5"), "5% 할인")),
        Optional.of(new ParsedPerformanceTier(new BigDecimal("300000"), new BigDecimal("700000"))),
        Optional.of(new ParsedTransactionCondition(
            new BigDecimal("10000"), null, new BigDecimal("50000"))),
        List.of(new ParsedLimit(ParsedLimit.Period.MONTHLY, ParsedLimit.Type.COUNT, new BigDecimal("2"))),
        Optional.of(new ParsedSchedule(
            java.util.Set.of(DayOfWeek.FRIDAY),
            LocalTime.of(9, 0), LocalTime.of(18, 0))),
        Optional.of(new ParsedTarget(ParsedTarget.Type.MERCHANT_CATEGORY, "CONVENIENCE_STORE")));
  }

  private BenefitCalculationContext context(int usedMonthlyCount, LocalDateTime approvedAt) {
    return new BenefitCalculationContext(
        new BigDecimal("100000"), BigDecimal.ONE, new BigDecimal("500000"), approvedAt, "CAFE",
        false, 0, usedMonthlyCount, true, true, false,
        Map.of(
            "AVAILABLE_FIELD", Set.of(
                "PAYMENT_AMOUNT", "PREVIOUS_MONTH_SPEND", "USED_DAILY_COUNT",
                "USED_MONTHLY_COUNT", "APPROVED_AT", "FOREIGN_TRANSACTION")));
  }
}
