package com.moca.mocabe.domain.benefit.model;

/** 직렬화된 보유카드 계산 안에서 조회한 룰별 일·월 사용 횟수다. */
public record BenefitUsageCounts(int dailyCount, int monthlyCount) {
  public BenefitUsageCounts {
    dailyCount = Math.max(dailyCount, 0);
    monthlyCount = Math.max(monthlyCount, 0);
  }
}
