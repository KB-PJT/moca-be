package com.moca.mocabe.domain.benefit.structuring;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moca.mocabe.domain.benefit.rule.BenefitRuleDefinition;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

/** 정규화 룰을 V22 JSON DSL로 한 번만 투영한다. 관계형 target은 별도 FK 정본이다. */
public class NormalizedRuleJsonFactory {
  private final ObjectMapper objectMapper;

  public NormalizedRuleJsonFactory() {
    this(new ObjectMapper());
  }

  NormalizedRuleJsonFactory(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String create(NormalizedRule normalizedRule) {
    ParsedReward reward = normalizedRule.reward().orElseThrow(
        () -> new IllegalArgumentException("확정 보상 없이 JSON 룰을 생성할 수 없습니다."));
    List<BenefitRuleDefinition.Condition> conditions = new ArrayList<>();
    normalizedRule.performanceTier().ifPresent(tier -> {
      conditions.add(condition("PREVIOUS_MONTH_SPEND", "GTE", tier.minimumKrw(), "PERFORMANCE_NOT_MET"));
      if (tier.maximumExclusiveKrw() != null) {
        conditions.add(condition("PREVIOUS_MONTH_SPEND", "LT", tier.maximumExclusiveKrw(), "PERFORMANCE_NOT_MET"));
      }
    });
    normalizedRule.transactionCondition().ifPresent(condition -> {
      if (condition.minimumPaymentKrw() != null) {
        conditions.add(condition("PAYMENT_AMOUNT", "GTE", condition.minimumPaymentKrw(), "MIN_PAYMENT_NOT_MET"));
      }
      if (condition.maximumEligiblePaymentKrw() != null) {
        conditions.add(condition("PAYMENT_AMOUNT", "LTE", condition.maximumEligiblePaymentKrw(), "CONDITION_NOT_MET"));
      }
    });
    normalizedRule.schedule().ifPresent(schedule -> {
      if (!schedule.days().isEmpty()) {
        conditions.add(new BenefitRuleDefinition.Condition(
            "DAY_OF_WEEK", "IN", null,
            schedule.days().stream().map(DayOfWeek::name).sorted().toList(), "CONDITION_NOT_MET"));
      }
      if (schedule.startTime() != null && schedule.endTime() != null) {
        conditions.add(new BenefitRuleDefinition.Condition(
            "APPROVED_TIME", "BETWEEN", null,
            List.of(schedule.startTime().toString(), schedule.endTime().toString()), "CONDITION_NOT_MET"));
      }
    });
    List<BenefitRuleDefinition.Limit> limits = normalizedRule.limits().stream()
        .filter(limit -> limit.type() == ParsedLimit.Type.COUNT)
        .filter(limit -> limit.period() == ParsedLimit.Period.DAILY || limit.period() == ParsedLimit.Period.MONTHLY)
        .map(limit -> new BenefitRuleDefinition.Limit(
            limit.period() == ParsedLimit.Period.DAILY ? "DAILY_USAGE_COUNT" : "MONTHLY_USAGE_COUNT",
            limit.value().toPlainString()))
        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    normalizedRule.transactionCondition().ifPresent(condition -> {
      if (condition.maximumBenefitBaseKrw() != null) {
        limits.add(new BenefitRuleDefinition.Limit(
            "TRANSACTION_BENEFIT_BASE", condition.maximumBenefitBaseKrw().toPlainString()));
      }
    });
    BenefitRuleDefinition definition = new BenefitRuleDefinition(
        1,
        new BenefitRuleDefinition.ConditionSet(conditions, List.of(), List.of()),
        reward(reward),
        limits);
    try {
      return objectMapper.writeValueAsString(definition);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("JSON 혜택 룰 직렬화에 실패했습니다.", exception);
    }
  }

  private BenefitRuleDefinition.Condition condition(
      String type, String operator, BigDecimal value, String reason) {
    return new BenefitRuleDefinition.Condition(type, operator, value.toPlainString(), List.of(), reason);
  }

  private BenefitRuleDefinition.Reward reward(ParsedReward reward) {
    return switch (reward.type()) {
      case PERCENT -> new BenefitRuleDefinition.Reward(
          "DISCOUNT", "KRW", "RATE",
          reward.value().movePointLeft(2).toPlainString(), null, null);
      case FIXED_KRW -> new BenefitRuleDefinition.Reward(
          "DISCOUNT", "KRW", "FIXED", null, reward.value().toPlainString(), null);
      case CASHBACK -> new BenefitRuleDefinition.Reward(
          "CASHBACK", "KRW", "FIXED", null, reward.value().toPlainString(), null);
      case POINT -> new BenefitRuleDefinition.Reward(
          "POINT", "POINT", "FIXED", null, reward.value().toPlainString(), null);
      case MILEAGE -> new BenefitRuleDefinition.Reward(
          "MILEAGE", "MILE", "FIXED", null, reward.value().toPlainString(), null);
    };
  }
}
