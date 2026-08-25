package com.moca.mocabe.domain.benefit.rule;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/** 임의 스크립트 실행 없이 허용된 JSON 스키마만 도메인 룰로 변환한다. */
public class BenefitRuleDefinitionParser {
  public static final int CURRENT_SCHEMA_VERSION = 1;
  private static final Set<String> BENEFIT_TYPES =
      Set.of("DISCOUNT", "CASHBACK", "POINT", "MILEAGE");
  private static final Set<String> REWARD_UNITS = Set.of("KRW", "POINT", "MILE");
  private static final Set<String> CALCULATIONS =
      Set.of("RATE", "FIXED", "PER_SPEND_UNIT", "PER_USAGE_UNIT");
  private static final Set<String> LIMIT_TYPES =
      Set.of("TRANSACTION_BENEFIT_BASE", "DAILY_USAGE_COUNT", "MONTHLY_USAGE_COUNT");
  private static final Set<String> NUMERIC_CONDITIONS =
      Set.of("PAYMENT_AMOUNT", "PREVIOUS_MONTH_SPEND", "USED_DAILY_COUNT", "USED_MONTHLY_COUNT");
  private static final Set<String> TARGET_CONDITIONS =
      Set.of("MERCHANT", "MERCHANT_CATEGORY", "TRANSACTION_TYPE");
  private static final Set<String> BOOLEAN_CONDITIONS =
      Set.of(
          "FOREIGN_TRANSACTION",
          "NEW_MEMBER_GRACE",
          "MERCHANT_ELIGIBLE",
          "PAYMENT_CHANNEL_ELIGIBLE");
  private static final Set<String> NUMERIC_OPERATORS = Set.of("GT", "GTE", "LT", "LTE", "EQ");

  private final ObjectMapper objectMapper;

  public BenefitRuleDefinitionParser() {
    this(new ObjectMapper());
  }

  public BenefitRuleDefinitionParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public BenefitRuleDefinition parse(String json) {
    if (json == null || json.isBlank()) {
      throw new IllegalArgumentException("JSON 혜택 룰이 비어 있습니다.");
    }
    try {
      BenefitRuleDefinition definition = objectMapper.readValue(json, BenefitRuleDefinition.class);
      validate(definition);
      return definition;
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("JSON 혜택 룰 형식이 올바르지 않습니다.", exception);
    }
  }

  private void validate(BenefitRuleDefinition definition) {
    if (definition.schemaVersion() != CURRENT_SCHEMA_VERSION) {
      throw new IllegalArgumentException("지원하지 않는 JSON 혜택 룰 버전입니다.");
    }
    BenefitRuleDefinition.Reward reward = definition.reward();
    if (reward == null
        || !BENEFIT_TYPES.contains(normalized(reward.benefitType()))
        || !REWARD_UNITS.contains(normalized(reward.rewardUnit()))
        || !CALCULATIONS.contains(normalized(reward.calculation()))) {
      throw new IllegalArgumentException("JSON 혜택 산식이 올바르지 않습니다.");
    }
    nonNegative(reward.rate(), "rate");
    nonNegative(reward.value(), "value");
    nonNegative(reward.spendUnitAmount(), "spendUnitAmount");
    validateRequiredRewardValues(reward);
    definition.conditions().all().forEach(this::validateCondition);
    definition.conditions().any().forEach(this::validateCondition);
    definition.conditions().none().forEach(this::validateCondition);
    definition.limits().forEach(this::validateLimit);
  }

  private void validateRequiredRewardValues(BenefitRuleDefinition.Reward reward) {
    String calculation = normalized(reward.calculation());
    if ("RATE".equals(calculation)) {
      decimal(reward.rate(), "rate");
      return;
    }
    decimal(reward.value(), "value");
    if ("PER_SPEND_UNIT".equals(calculation)
        && decimal(reward.spendUnitAmount(), "spendUnitAmount").signum() <= 0) {
      throw new IllegalArgumentException("spendUnitAmount는 0보다 커야 합니다.");
    }
  }

  private void validateCondition(BenefitRuleDefinition.Condition condition) {
    String type = normalized(condition.type());
    String operator = normalized(condition.operator());
    if (NUMERIC_CONDITIONS.contains(type)) {
      requireOperator(operator, NUMERIC_OPERATORS);
      decimal(condition.value(), "condition.value");
      return;
    }
    if (TARGET_CONDITIONS.contains(type)) {
      requireOperator(operator, Set.of("EQ", "IN"));
      requireTargetValue(condition, operator);
      return;
    }
    if ("DAY_OF_WEEK".equals(type)) {
      requireOperator(operator, Set.of("IN"));
      requireValues(condition.values(), 1);
      condition.values().forEach(this::dayOfWeek);
      return;
    }
    if ("APPROVED_TIME".equals(type)) {
      requireOperator(operator, Set.of("BETWEEN"));
      requireValues(condition.values(), 2);
      condition.values().forEach(this::localTime);
      return;
    }
    if (BOOLEAN_CONDITIONS.contains(type)) {
      requireOperator(operator, Set.of("EQ"));
      if (!Set.of("TRUE", "FALSE").contains(normalized(condition.value()))) {
        throw new IllegalArgumentException("불리언 조건값은 true 또는 false여야 합니다.");
      }
      return;
    }
    throw new IllegalArgumentException("지원하지 않는 JSON 혜택 조건입니다.");
  }

  private void validateLimit(BenefitRuleDefinition.Limit limit) {
    if (limit == null || !LIMIT_TYPES.contains(normalized(limit.type()))) {
      throw new IllegalArgumentException("지원하지 않는 JSON 혜택 한도입니다.");
    }
    nonNegative(limit.value(), "limit.value");
    if (!"TRANSACTION_BENEFIT_BASE".equals(normalized(limit.type()))) {
      try {
        decimal(limit.value(), "limit.value").intValueExact();
      } catch (ArithmeticException exception) {
        throw new IllegalArgumentException("횟수 한도는 정수 범위여야 합니다.", exception);
      }
    }
  }

  private void nonNegative(String value, String field) {
    if (value != null && !value.isBlank() && decimal(value, field).signum() < 0) {
      throw new IllegalArgumentException(field + "는 음수일 수 없습니다.");
    }
  }

  private BigDecimal decimal(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + "가 비어 있습니다.");
    }
    try {
      return new BigDecimal(value);
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(field + "는 숫자여야 합니다.", exception);
    }
  }

  private void requireOperator(String operator, Set<String> allowed) {
    if (!allowed.contains(operator)) {
      throw new IllegalArgumentException("지원하지 않는 JSON 혜택 조건 연산자입니다.");
    }
  }

  private void requireTargetValue(BenefitRuleDefinition.Condition condition, String operator) {
    if ("EQ".equals(operator)) {
      if (condition.value() == null || condition.value().isBlank()) {
        throw new IllegalArgumentException("대상 조건값이 비어 있습니다.");
      }
      return;
    }
    requireValues(condition.values(), 1);
  }

  private void requireValues(List<String> values, int requiredSize) {
    if (values == null
        || values.size() < requiredSize
        || (requiredSize > 1 && values.size() != requiredSize)
        || values.stream().anyMatch(value -> value == null || value.isBlank())) {
      throw new IllegalArgumentException("JSON 혜택 조건값 목록이 올바르지 않습니다.");
    }
  }

  private void dayOfWeek(String value) {
    try {
      DayOfWeek.valueOf(normalized(value));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("요일 조건값이 올바르지 않습니다.", exception);
    }
  }

  private void localTime(String value) {
    try {
      LocalTime.parse(value);
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("시간 조건값이 올바르지 않습니다.", exception);
    }
  }

  private String normalized(String value) {
    return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
  }
}
