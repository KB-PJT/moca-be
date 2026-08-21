package com.moca.mocabe.domain.benefit.service;

import com.moca.mocabe.domain.benefit.model.BenefitHistoryRow;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 계산 원장의 여러 후보 결과에서 승인별로 목록에 표시할 대표 결과를 선택한다. */
public class BenefitHistoryRepresentativeSelector {

  private static final Comparator<BenefitHistoryRow> REPRESENTATIVE_ORDER =
      Comparator.comparingInt(BenefitHistoryRepresentativeSelector::statusPriority)
          .thenComparing(Comparator.comparingLong(BenefitHistoryRow::getBenefitAmount).reversed())
          .thenComparing(Comparator.comparingLong(BenefitHistoryRow::getMissedBenefitAmount).reversed())
          .thenComparing(BenefitHistoryRow::getBenefitHistoryId);

  public List<BenefitHistoryRow> select(List<BenefitHistoryRow> candidates) {
    Map<String, BenefitHistoryRow> representatives = new LinkedHashMap<>();
    for (BenefitHistoryRow candidate : candidates) {
      representatives.merge(
          candidate.getApprovalId(),
          candidate,
          (current, next) -> REPRESENTATIVE_ORDER.compare(current, next) <= 0 ? current : next);
    }
    return List.copyOf(representatives.values());
  }

  private static int statusPriority(BenefitHistoryRow row) {
    return switch (row.getCalculationStatus()) {
      case "APPLIED" -> 0;
      case "PARTIALLY_APPLIED" -> 1;
      case "NOT_APPLIED" -> 2;
      default -> 3;
    };
  }
}
