package com.moca.mocabe.domain.benefit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("DREAM 영역 적립률 계산")
class BenefitAreaRewardCalculatorTest {
  private final BenefitAreaRewardCalculator calculator = new BenefitAreaRewardCalculator();

  @Test
  @DisplayName("전월 실적을 충족한 일반 DREAM 영역은 0.6%를 적립한다")
  void appliesDreamRate() {
    assertRate("0.006", calculator.rate(true, true, false));
  }

  @Test
  @DisplayName("최다 DREAM 영역은 1.0%를 적립한다")
  void appliesTopDreamRate() {
    assertRate("0.010", calculator.rate(true, true, true));
  }

  @Test
  @DisplayName("전월 실적 미달 또는 DREAM 외 영역은 기본 0.2%만 적립한다")
  void appliesBaseRateWhenExtraUnavailable() {
    assertRate("0.002", calculator.rate(false, true, true));
    assertRate("0.002", calculator.rate(true, false, true));
  }

  @ParameterizedTest(name = "전월 {0}원은 추가 적립 한도 {1}포인트")
  @CsvSource({
      "199999,0", "200000,5000", "200001,5000",
      "399999,5000", "400000,15000", "799999,15000", "800000,30000"
  })
  @DisplayName("전월 실적 경계에 맞는 통합 추가 적립 한도를 적용한다")
  void appliesExtraLimitTier(String previousSpend, String expectedRemaining) {
    BenefitAreaRewardCalculator.RewardAllocation allocation = calculator.allocate(
        BigDecimal.ZERO, new BigDecimal(previousSpend), true, true, BigDecimal.ZERO);

    assertEquals(0, new BigDecimal(expectedRemaining)
        .compareTo(allocation.remainingExtraLimit()));
  }

  @Test
  @DisplayName("통합 한도 잔액보다 추가 적립액이 크면 기본 적립은 유지하고 추가분만 부분 적용한다")
  void capsOnlyExtraReward() {
    BenefitAreaRewardCalculator.RewardAllocation allocation = calculator.allocate(
        new BigDecimal("100000"), new BigDecimal("200000"), true, true,
        new BigDecimal("4500"));

    assertEquals(0, new BigDecimal("1000").compareTo(allocation.rawReward()));
    assertEquals(0, new BigDecimal("700").compareTo(allocation.appliedReward()));
    assertEquals(0, BigDecimal.ZERO.compareTo(allocation.remainingExtraLimit()));
  }

  private void assertRate(String expected, BigDecimal actual) {
    assertEquals(0, new BigDecimal(expected).compareTo(actual));
  }
}
