package com.moca.mocabe.domain.benefit.service;

import com.moca.mocabe.domain.benefit.model.BenefitAreaSpendRow;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/** 월간 혜택 영역 사용액으로 최다 이용 영역을 결정한다. */
public class BenefitAreaRankSelector {
  public BenefitAreaSpendRow selectTop(List<BenefitAreaSpendRow> spends) {
    if (spends == null || spends.isEmpty()) {
      return null;
    }
    return spends.stream()
        .filter(spend -> spend != null && spend.eligibleAmountKrw() != null)
        .filter(spend -> spend.eligibleAmountKrw().compareTo(BigDecimal.ZERO) > 0)
        .min(Comparator
            .comparing(BenefitAreaSpendRow::eligibleAmountKrw, Comparator.reverseOrder())
            .thenComparingInt(BenefitAreaSpendRow::displayOrder)
            .thenComparing(BenefitAreaSpendRow::areaKey))
        .orElse(null);
  }
}
