package com.moca.mocabe.domain.benefit.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 특정 결제 상황에서 혜택을 계산하기 위한 입력값이다. */
public record BenefitCalculationContext(
    BigDecimal paymentAmount,
    BigDecimal usageQuantity,
    BigDecimal previousMonthSpend,
    LocalDateTime approvedAt,
    String mocaCategory,
    boolean newMemberGracePeriod,
    int usedDailyCount,
    int usedMonthlyCount,
    boolean merchantEligible,
    boolean paymentChannelEligible,
    boolean foreignTransaction,
    Map<String, Set<String>> targetAttributes) {

  public BenefitCalculationContext(
      BigDecimal paymentAmount,
      BigDecimal usageQuantity,
      BigDecimal previousMonthSpend,
      LocalDateTime approvedAt,
      String mocaCategory) {
    this(
        paymentAmount,
        usageQuantity,
        previousMonthSpend,
        approvedAt,
        mocaCategory,
        false,
        0,
        0,
        true,
        true,
        false,
        Map.of());
  }

  public BenefitCalculationContext(
      BigDecimal paymentAmount,
      BigDecimal usageQuantity,
      BigDecimal previousMonthSpend,
      LocalDateTime approvedAt,
      String mocaCategory,
      boolean newMemberGracePeriod,
      int usedDailyCount,
      int usedMonthlyCount,
      boolean merchantEligible,
      boolean paymentChannelEligible) {
    this(
        paymentAmount,
        usageQuantity,
        previousMonthSpend,
        approvedAt,
        mocaCategory,
        newMemberGracePeriod,
        usedDailyCount,
        usedMonthlyCount,
        merchantEligible,
        paymentChannelEligible,
        false,
        Map.of());
  }

  public BenefitCalculationContext {
    // 계산 입력이 비어도 BigDecimal 비교와 연산이 안전하게 동작하도록 0으로 보정한다.
    paymentAmount = paymentAmount == null ? BigDecimal.ZERO : paymentAmount;
    usageQuantity = usageQuantity == null ? BigDecimal.ZERO : usageQuantity;
    previousMonthSpend = previousMonthSpend == null ? BigDecimal.ZERO : previousMonthSpend;
    usedDailyCount = Math.max(usedDailyCount, 0);
    usedMonthlyCount = Math.max(usedMonthlyCount, 0);
    targetAttributes = normalizeAttributes(targetAttributes);
  }

  public boolean hasTarget(String targetType, String targetCode) {
    String normalizedType = normalize(targetType);
    String normalizedCode = normalize(targetCode);
    if ("ALL_MERCHANTS".equals(normalizedType)) {
      return true;
    }
    if ("MERCHANT_CATEGORY".equals(normalizedType)
        && normalizedCode.equals(normalize(mocaCategory))) {
      return true;
    }
    return targetAttributes.getOrDefault(normalizedType, Set.of()).contains(normalizedCode);
  }

  private static Map<String, Set<String>> normalizeAttributes(Map<String, Set<String>> attributes) {
    if (attributes == null || attributes.isEmpty()) {
      return Map.of();
    }
    Map<String, Set<String>> normalized = new HashMap<>();
    attributes.forEach(
        (type, values) ->
            normalized.put(
                normalize(type),
                values == null
                    ? Set.of()
                    : values.stream()
                        .map(BenefitCalculationContext::normalize)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet())));
    return Map.copyOf(normalized);
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }
}
