package com.moca.mocabe.domain.benefit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.moca.mocabe.domain.benefit.model.BenefitHistoryRow;
import java.util.List;
import org.junit.jupiter.api.Test;

class BenefitHistoryRepresentativeSelectorTest {

  private final BenefitHistoryRepresentativeSelector selector =
      new BenefitHistoryRepresentativeSelector();

  @Test
  void selectsAppliedBenefitInsteadOfRejectedCandidatesForSameApproval() {
    BenefitHistoryRow rejected = row("outcome-1", "approval-1", "NOT_APPLIED", 0, 100);
    BenefitHistoryRow applied = row("usage-1", "approval-1", "APPLIED", 7, 0);

    List<BenefitHistoryRow> selected = selector.select(List.of(rejected, applied));

    assertEquals(1, selected.size());
    assertEquals("usage-1", selected.get(0).getBenefitHistoryId());
  }

  @Test
  void keepsAppliedBenefitWhenRejectedCandidateComesLater() {
    BenefitHistoryRow applied = row("usage-1", "approval-1", "APPLIED", 7, 0);
    BenefitHistoryRow rejected = row("outcome-1", "approval-1", "NOT_APPLIED", 0, 100);

    assertEquals(
        "usage-1", selector.select(List.of(applied, rejected)).get(0).getBenefitHistoryId());
  }

  @Test
  void prefersPartialBenefitToRejectedAndUncalculatedCandidates() {
    BenefitHistoryRow uncalculated = row("approval-1", "approval-1", "NOT_CALCULATED", 0, 0);
    BenefitHistoryRow rejected = row("outcome-1", "approval-1", "NOT_APPLIED", 0, 100);
    BenefitHistoryRow partial = row("outcome-2", "approval-1", "PARTIALLY_APPLIED", 5, 5);

    assertEquals(
        "outcome-2",
        selector
            .select(List.of(uncalculated, rejected, partial))
            .get(0)
            .getBenefitHistoryId());
  }

  @Test
  void selectsLargestMissedBenefitWhenNoCandidateWasApplied() {
    BenefitHistoryRow small = row("outcome-1", "approval-1", "NOT_APPLIED", 0, 10);
    BenefitHistoryRow large = row("outcome-2", "approval-1", "NOT_APPLIED", 0, 20);

    assertEquals(
        "outcome-2", selector.select(List.of(small, large)).get(0).getBenefitHistoryId());
  }

  @Test
  void keepsOneRepresentativeForEachApproval() {
    BenefitHistoryRow first = row("usage-1", "approval-1", "APPLIED", 7, 0);
    BenefitHistoryRow second = row("usage-2", "approval-2", "APPLIED", 10, 0);

    assertEquals(2, selector.select(List.of(first, second)).size());
  }

  private BenefitHistoryRow row(
      String historyId, String approvalId, String status, long benefit, long missed) {
    BenefitHistoryRow row = new BenefitHistoryRow();
    row.setBenefitHistoryId(historyId);
    row.setApprovalId(approvalId);
    row.setCalculationStatus(status);
    row.setBenefitAmount(benefit);
    row.setMissedBenefitAmount(missed);
    return row;
  }
}
