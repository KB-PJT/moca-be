package com.moca.mocabe.domain.benefit.model;

import com.moca.mocabe.domain.benefit.type.BenefitBasis;
import com.moca.mocabe.domain.benefit.type.BenefitPromotionCondition;
import com.moca.mocabe.domain.benefit.type.BenefitType;
import com.moca.mocabe.domain.benefit.type.RewardUnit;
import java.math.BigDecimal;
import java.util.Set;

/**
 * 카드 혜택 계산에 필요한 룰 데이터다.
 *
 * <p>DB에 저장된 혜택 룰 한 건을 애플리케이션 계산 모델로 옮긴 형태이며, 프로모션성 예외 조건은 별도 조건 테이블에서 검증하거나 안내 문구로 처리한다.
 */
public record BenefitRule(
    String ruleId,
    BenefitType benefitType,
    BenefitBasis benefitBasis,
    RewardUnit rewardUnit,
    // RATE 계산에 사용하는 비율이다. 10%는 0.10으로 저장한다.
    BigDecimal rewardRate,
    // FIXED 또는 PER_SPEND_UNIT 계산에 사용하는 혜택값이다.
    BigDecimal rewardValue,
    // PER_SPEND_UNIT 계산에서 기준이 되는 결제 단위다. 예: 1,000원당 1마일.
    BigDecimal spendUnitAmount,
    BigDecimal maximumBenefitBaseAmount,
    BigDecimal minimumPaymentAmount,
    BigDecimal requiredPreviousMonthSpend,
    BigDecimal monthlyLimitValue,
    BigDecimal usedMonthlyValue,
    BenefitPromotionCondition promotionCondition,
    Set<String> mocaCategories,
    int dailyUsageLimit,
    int monthlyUsageLimit,
    boolean merchantEligibilityRequired,
    boolean paymentChannelEligibilityRequired,
    Set<BenefitRuleTarget> targets,
    Set<BenefitRuleSchedule> schedules) {

  public BenefitRule(
      String ruleId,
      BenefitType benefitType,
      BenefitBasis benefitBasis,
      RewardUnit rewardUnit,
      BigDecimal rewardRate,
      BigDecimal rewardValue,
      BigDecimal spendUnitAmount,
      BigDecimal maximumBenefitBaseAmount,
      BigDecimal minimumPaymentAmount,
      BigDecimal requiredPreviousMonthSpend,
      BigDecimal monthlyLimitValue,
      BigDecimal usedMonthlyValue,
      BenefitPromotionCondition promotionCondition,
      Set<String> mocaCategories) {
    this(
        ruleId,
        benefitType,
        benefitBasis,
        rewardUnit,
        rewardRate,
        rewardValue,
        spendUnitAmount,
        maximumBenefitBaseAmount,
        minimumPaymentAmount,
        requiredPreviousMonthSpend,
        monthlyLimitValue,
        usedMonthlyValue,
        promotionCondition,
        mocaCategories,
        0,
        0,
        false,
        false,
        Set.of(),
        Set.of());
  }

  public BenefitRule(
      String ruleId,
      BenefitType benefitType,
      BenefitBasis benefitBasis,
      RewardUnit rewardUnit,
      BigDecimal rewardRate,
      BigDecimal rewardValue,
      BigDecimal spendUnitAmount,
      BigDecimal maximumBenefitBaseAmount,
      BigDecimal minimumPaymentAmount,
      BigDecimal requiredPreviousMonthSpend,
      BigDecimal monthlyLimitValue,
      BigDecimal usedMonthlyValue,
      BenefitPromotionCondition promotionCondition,
      Set<String> mocaCategories,
      int dailyUsageLimit,
      int monthlyUsageLimit,
      boolean merchantEligibilityRequired,
      boolean paymentChannelEligibilityRequired) {
    this(
        ruleId,
        benefitType,
        benefitBasis,
        rewardUnit,
        rewardRate,
        rewardValue,
        spendUnitAmount,
        maximumBenefitBaseAmount,
        minimumPaymentAmount,
        requiredPreviousMonthSpend,
        monthlyLimitValue,
        usedMonthlyValue,
        promotionCondition,
        mocaCategories,
        dailyUsageLimit,
        monthlyUsageLimit,
        merchantEligibilityRequired,
        paymentChannelEligibilityRequired,
        Set.of(),
        Set.of());
  }

  public BenefitRule {
    // 외부 데이터가 비어 있어도 계산기가 null을 직접 다루지 않도록 기본값을 보정한다.
    rewardRate = rewardRate == null ? BigDecimal.ZERO : rewardRate;
    rewardValue = rewardValue == null ? BigDecimal.ZERO : rewardValue;
    spendUnitAmount = spendUnitAmount == null ? BigDecimal.ZERO : spendUnitAmount;
    maximumBenefitBaseAmount =
        maximumBenefitBaseAmount == null ? BigDecimal.ZERO : maximumBenefitBaseAmount;
    minimumPaymentAmount = minimumPaymentAmount == null ? BigDecimal.ZERO : minimumPaymentAmount;
    requiredPreviousMonthSpend =
        requiredPreviousMonthSpend == null ? BigDecimal.ZERO : requiredPreviousMonthSpend;
    monthlyLimitValue = monthlyLimitValue == null ? BigDecimal.ZERO : monthlyLimitValue;
    usedMonthlyValue = usedMonthlyValue == null ? BigDecimal.ZERO : usedMonthlyValue;
    promotionCondition =
        promotionCondition == null ? BenefitPromotionCondition.NONE : promotionCondition;
    mocaCategories = mocaCategories == null ? Set.of() : Set.copyOf(mocaCategories);
    targets = targets == null ? Set.of() : Set.copyOf(targets);
    schedules = schedules == null ? Set.of() : Set.copyOf(schedules);
    dailyUsageLimit = Math.max(dailyUsageLimit, 0);
    monthlyUsageLimit = Math.max(monthlyUsageLimit, 0);
  }

  public boolean matchesCategory(String mocaCategory) {
    // 카테고리가 비어 있는 룰은 모든 카테고리에 적용되는 공통 혜택으로 본다.
    return mocaCategories.isEmpty() || mocaCategories.contains(mocaCategory);
  }
}
