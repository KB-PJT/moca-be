package com.moca.mocabe.domain.benefit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.moca.mocabe.domain.benefit.model.BenefitLimitTierCandidate;
import com.moca.mocabe.domain.benefit.model.BenefitLimitTierSelection;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BenefitLimitTierSelectorTest {
  private final BenefitLimitTierSelector selector = new BenefitLimitTierSelector();

  @Test
  @DisplayName("전월 자격을 통과한 후보 중 당월 실적이 가장 높은 tier를 선택한다")
  void selectsTierByCurrentMonthSpendAfterPreviousSpendEligibility() {
    List<BenefitLimitTierCandidate> candidates = List.of(
        tier("base", 5_000, 0, 0), tier("middle", 10_000, 0, 300_000),
        tier("high", 15_000, 500_000, 500_000));

    assertEquals("base",
        selector.select(candidates, money(300_000), money(299_999)).limit().limitPolicyId());
    assertEquals("middle",
        selector.select(candidates, money(300_000), money(300_000)).limit().limitPolicyId());
    assertEquals("high",
        selector.select(candidates, money(500_000), money(500_000)).limit().limitPolicyId());
    assertEquals("middle",
        selector.select(candidates, money(400_000), money(600_000)).limit().limitPolicyId());
  }

  @Test
  @DisplayName("정책 없음과 전월 실적 미달 및 당월 tier 미도달을 구분한다")
  void distinguishesWhyNoTierWasSelected() {
    assertEquals(BenefitLimitTierSelection.Status.NO_POLICY,
        selector.select(List.of(), money(0), money(0)).status());
    assertEquals(BenefitLimitTierSelection.Status.PERFORMANCE_NOT_MET,
        selector.select(List.of(tier("tier", 5_000, 300_000, 0)),
            money(299_999), money(0)).status());
    assertEquals(BenefitLimitTierSelection.Status.CURRENT_TIER_NOT_MET,
        selector.select(List.of(tier("tier", 5_000, 0, 300_000)),
            money(0), money(299_999)).status());
  }

  @Test
  @DisplayName("적용 월 정책에 따라 일반월과 가족행사월 tier를 분리한다")
  void selectsTierByApplicableMonth() {
    List<BenefitLimitTierCandidate> candidates = List.of(
        new BenefitLimitTierCandidate("regular", null, money(15_000), money(800_000),
            money(0), "[1,2,3,4,6,7,8,9,10,11]"),
        new BenefitLimitTierCandidate("family", null, money(20_000), money(800_000),
            money(0), "[5,12]"));

    assertEquals("regular", selector.select(candidates, money(800_000), money(0),
        LocalDate.of(2026, 8, 21)).limit().limitPolicyId());
    assertEquals("family", selector.select(candidates, money(800_000), money(0),
        LocalDate.of(2026, 5, 21)).limit().limitPolicyId());
  }

  private BenefitLimitTierCandidate tier(String id, int limit, int previous, int current) {
    return new BenefitLimitTierCandidate(id, null, money(limit), money(previous), money(current));
  }

  private BigDecimal money(int value) {
    return BigDecimal.valueOf(value);
  }
}
