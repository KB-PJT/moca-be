package com.moca.mocabe.domain.benefit.service;

import com.moca.mocabe.domain.benefit.mapper.BenefitCalculationMapper;
import com.moca.mocabe.domain.benefit.model.BenefitAreaSpendRow;
import java.time.YearMonth;
import java.math.BigDecimal;
import java.util.List;

/** 승인 내역에서 월간 혜택 영역 사용액을 조회하고 최다 영역을 결정한다. */
public class BenefitAreaSpendService {
  private final BenefitCalculationMapper mapper;
  private final BenefitAreaRankSelector rankSelector;

  public BenefitAreaSpendService(BenefitCalculationMapper mapper) {
    this(mapper, new BenefitAreaRankSelector());
  }

  BenefitAreaSpendService(BenefitCalculationMapper mapper, BenefitAreaRankSelector rankSelector) {
    this.mapper = mapper;
    this.rankSelector = rankSelector;
  }

  public List<BenefitAreaSpendRow> findMonthlySpends(
      String userCardId, String areaGroupKey, YearMonth usageMonth) {
    if (mapper == null || userCardId == null || areaGroupKey == null || usageMonth == null) {
      return List.of();
    }
    return mapper.findMonthlyBenefitAreaSpends(userCardId, areaGroupKey, usageMonth.toString());
  }

  public BenefitAreaSpendRow findTopArea(
      String userCardId, String areaGroupKey, YearMonth usageMonth) {
    return rankSelector.selectTop(findMonthlySpends(userCardId, areaGroupKey, usageMonth));
  }

  public List<String> findAreaKeysForApproval(String approvalId, String areaGroupKey) {
    if (mapper == null || approvalId == null || areaGroupKey == null) {
      return List.of();
    }
    return mapper.findBenefitAreaKeysForApproval(approvalId, areaGroupKey);
  }

  public void recordApproval(String approvalId, String userCardId, BigDecimal amount, YearMonth usageMonth) {
    if (mapper == null || approvalId == null || userCardId == null || amount == null || usageMonth == null) {
      return;
    }
    for (String areaKey : findAreaKeysForApproval(approvalId, "DREAM")) {
      int inserted = mapper.insertBenefitAreaSpendEventIfAbsent(
          approvalId, userCardId, "DREAM", areaKey, usageMonth.toString(), amount);
      if (inserted > 0) {
        mapper.upsertMonthlyBenefitAreaSpend(
            userCardId, "DREAM", areaKey, usageMonth.toString(), amount);
      }
    }
  }
}
