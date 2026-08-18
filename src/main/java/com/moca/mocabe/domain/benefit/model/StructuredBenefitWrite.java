package com.moca.mocabe.domain.benefit.model;

import java.math.BigDecimal;

/** parser가 확정한 단순 rule을 기존 benefit schema에 저장하기 위한 입력이다. */
public record StructuredBenefitWrite(
    String ruleId,
    String offerId,
    String benefitId,
    String ruleName,
    String rewardType,
    String valueType,
    BigDecimal rewardValue,
    String rewardUnit,
    BigDecimal previousSpendMinKrw,
    BigDecimal transactionMinKrw,
    BigDecimal transactionMaxKrw,
    String ruleDefinitionJson,
    String targetCode,
    String monthlyLimitPolicyId,
    BigDecimal monthlyRewardLimit) { }
