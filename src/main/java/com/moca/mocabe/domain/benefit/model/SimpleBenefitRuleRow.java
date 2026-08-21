package com.moca.mocabe.domain.benefit.model;

import java.math.BigDecimal;

/** 자동 계산이 허용된 단순 혜택 룰의 평탄화 조회 행이다. */
public record SimpleBenefitRuleRow(
    String ruleId,
    String offerId,
    String offerName,
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
        ruleId, offerId, null, rewardType, rewardUnit, rewardValue, rewardBasisAmount,
        previousSpendMinKrw, transactionMinKrw, targetType, targetCode, conditionGroup,
        matchMode, null, null, "LEGACY");
  }

  public SimpleBenefitRuleRow(String ruleId, String offerId, String rewardType, String rewardUnit,
                              BigDecimal rewardValue, BigDecimal rewardBasisAmount,
                              BigDecimal previousSpendMinKrw, BigDecimal transactionMinKrw,
                              String targetType, String targetCode, int conditionGroup) {
    this(ruleId, offerId, null, rewardType, rewardUnit, rewardValue, rewardBasisAmount,
        previousSpendMinKrw, transactionMinKrw, targetType, targetCode, conditionGroup, "include",
        null, null, "LEGACY");
  }

  /** offerName 필드 추가 전 테스트·호출부와의 호환 생성자다. */
  public SimpleBenefitRuleRow(
      String ruleId, String offerId, String rewardType, String rewardUnit,
      BigDecimal rewardValue, BigDecimal rewardBasisAmount, BigDecimal previousSpendMinKrw,
      BigDecimal transactionMinKrw, String targetType, String targetCode, int conditionGroup,
      String matchMode, BigDecimal transactionMaxKrw, String ruleDefinitionJson,
      String ruleSupportStatus) {
    this(ruleId, offerId, null, rewardType, rewardUnit, rewardValue, rewardBasisAmount,
        previousSpendMinKrw, transactionMinKrw, targetType, targetCode, conditionGroup,
        matchMode, transactionMaxKrw, ruleDefinitionJson, ruleSupportStatus);
  }
}
