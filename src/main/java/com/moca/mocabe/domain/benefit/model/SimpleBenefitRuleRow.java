package com.moca.mocabe.domain.benefit.model;

import java.math.BigDecimal;

/** 자동 계산이 허용된 단순 혜택 룰의 평탄화 조회 행이다. */
public record SimpleBenefitRuleRow(
    String ruleId,
    String offerId,
    String rewardType,
    String rewardUnit,
    BigDecimal rewardValue,
    BigDecimal rewardBasisAmount,
    BigDecimal previousSpendMinKrw,
    BigDecimal transactionMinKrw,
    String targetType,
    String targetCode,
    int conditionGroup,
    String matchMode,
    BigDecimal transactionMaxKrw,
    String ruleDefinitionJson,
    String ruleSupportStatus) {

  public SimpleBenefitRuleRow(
      String ruleId,
      String offerId,
      String rewardType,
      String rewardUnit,
      BigDecimal rewardValue,
      BigDecimal rewardBasisAmount,
      BigDecimal previousSpendMinKrw,
      BigDecimal transactionMinKrw,
      String targetType,
      String targetCode,
      int conditionGroup,
      String matchMode) {
    this(
        ruleId, offerId, rewardType, rewardUnit, rewardValue, rewardBasisAmount,
        previousSpendMinKrw, transactionMinKrw, targetType, targetCode, conditionGroup,
        matchMode, null, null, "LEGACY");
  }

  public SimpleBenefitRuleRow(String ruleId, String offerId, String rewardType, String rewardUnit,
                              BigDecimal rewardValue, BigDecimal rewardBasisAmount,
                              BigDecimal previousSpendMinKrw, BigDecimal transactionMinKrw,
                              String targetType, String targetCode, int conditionGroup) {
    this(ruleId, offerId, rewardType, rewardUnit, rewardValue, rewardBasisAmount,
        previousSpendMinKrw, transactionMinKrw, targetType, targetCode, conditionGroup, "include",
        null, null, "LEGACY");
  }
}
