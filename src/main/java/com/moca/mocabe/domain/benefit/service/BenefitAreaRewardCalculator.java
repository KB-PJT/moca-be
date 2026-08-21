package com.moca.mocabe.domain.benefit.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** DREAM 영역 판정 결과를 최종 포인트 적립률로 변환한다. */
public class BenefitAreaRewardCalculator {
  private static final BigDecimal BASE_RATE = new BigDecimal("0.002");
  private static final BigDecimal DREAM_EXTRA_RATE = new BigDecimal("0.004");

  public BigDecimal rate(boolean previousSpendEligible, boolean dreamArea, boolean topArea) {
    if (!previousSpendEligible || !dreamArea) {
      return BASE_RATE;
    }
    return topArea ? BASE_RATE.add(DREAM_EXTRA_RATE).add(DREAM_EXTRA_RATE)
        : BASE_RATE.add(DREAM_EXTRA_RATE);
  }

  public RewardAllocation allocate(
      BigDecimal amount, BigDecimal previousSpend, boolean dreamArea, boolean topArea,
      BigDecimal usedExtraReward) {
    BigDecimal base = amount.multiply(BASE_RATE).setScale(0, RoundingMode.FLOOR);
    BigDecimal raw = amount.multiply(rate(previousSpend.compareTo(new BigDecimal("200000")) >= 0,
        dreamArea, topArea)).setScale(0, RoundingMode.FLOOR);
    BigDecimal extra = raw.subtract(base).max(BigDecimal.ZERO);
    BigDecimal limit = extraLimit(previousSpend);
    BigDecimal remaining = limit.subtract(zero(usedExtraReward)).max(BigDecimal.ZERO);
    BigDecimal appliedExtra = extra.min(remaining);
    return new RewardAllocation(raw, base.add(appliedExtra),
        remaining.subtract(appliedExtra).max(BigDecimal.ZERO));
  }

  private BigDecimal extraLimit(BigDecimal previousSpend) {
    if (previousSpend.compareTo(new BigDecimal("800000")) >= 0) {
      return new BigDecimal("30000");
    }
    if (previousSpend.compareTo(new BigDecimal("400000")) >= 0) {
      return new BigDecimal("15000");
    }
    if (previousSpend.compareTo(new BigDecimal("200000")) >= 0) {
      return new BigDecimal("5000");
    }
    return BigDecimal.ZERO;
  }

  private BigDecimal zero(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  public record RewardAllocation(
      BigDecimal rawReward, BigDecimal appliedReward, BigDecimal remainingExtraLimit) { }
}
