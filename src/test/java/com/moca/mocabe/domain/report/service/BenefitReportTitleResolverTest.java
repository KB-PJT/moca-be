package com.moca.mocabe.domain.report.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.moca.mocabe.domain.report.model.MissedBenefitRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BenefitReportTitleResolverTest {

  private final BenefitReportTitleResolver resolver = new BenefitReportTitleResolver();

  @Test
  @DisplayName("report_title을 가장 우선하고 공백 제목은 건너뛴다")
  void prefersReportTitleAndSkipsBlankCandidate() {
    MissedBenefitRow row =
        new MissedBenefitRow(
            "rule", "  ", " 혜택 카테고리 ", "상세 룰", "오퍼", "DISCOUNT", 0, 10);

    assertEquals("혜택 카테고리", resolver.resolve(row));
  }

  @Test
  @DisplayName("모든 제목 후보가 없으면 null을 반환한다")
  void returnsNullWhenNoTitleExists() {
    MissedBenefitRow row =
        new MissedBenefitRow("rule", null, null, " ", "", "DISCOUNT", 0, 10);

    assertEquals(null, resolver.resolve(row));
  }
}
