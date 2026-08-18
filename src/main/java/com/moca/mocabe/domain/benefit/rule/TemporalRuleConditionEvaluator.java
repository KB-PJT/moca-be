package com.moca.mocabe.domain.benefit.rule;

import com.moca.mocabe.domain.benefit.model.BenefitCalculationContext;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 승인시각으로 확인할 수 있는 요일과 시간 조건을 서울 시간 기준으로 판정한다. */
public class TemporalRuleConditionEvaluator implements RuleConditionEvaluator {
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final Set<String> TYPES = Set.of("DAY_OF_WEEK", "APPROVED_TIME");

  @Override
  public boolean supports(String conditionType) {
    return TYPES.contains(normalized(conditionType));
  }

  @Override
  public RuleConditionResult evaluate(
      BenefitRuleDefinition.Condition condition,
      BenefitCalculationContext context) {
    if (!context.hasTarget("AVAILABLE_FIELD", "APPROVED_AT") || context.approvedAt() == null) {
      return RuleConditionResult.unavailable();
    }
    java.time.LocalDateTime approvedAt =
        context.approvedAt().atZone(ZoneOffset.UTC).withZoneSameInstant(SEOUL).toLocalDateTime();
    boolean matched = switch (normalized(condition.type())) {
      case "DAY_OF_WEEK" -> condition.values().stream()
          .map(this::normalized)
          .anyMatch(value -> value.equals(approvedAt.getDayOfWeek().name()));
      case "APPROVED_TIME" -> between(approvedAt.toLocalTime(), condition.values());
      default -> false;
    };
    return matched
        ? RuleConditionResult.matched()
        : RuleConditionResult.notMatched(RuleEvaluationSupport.rejectionReason(condition));
  }

  private boolean between(LocalTime actual, List<String> values) {
    if (values.size() != 2) {
      return false;
    }
    try {
      LocalTime start = LocalTime.parse(values.get(0));
      LocalTime end = LocalTime.parse(values.get(1));
      if (start.isBefore(end)) {
        return !actual.isBefore(start) && actual.isBefore(end);
      }
      return !actual.isBefore(start) || actual.isBefore(end);
    } catch (RuntimeException exception) {
      return false;
    }
  }

  private String normalized(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }
}
