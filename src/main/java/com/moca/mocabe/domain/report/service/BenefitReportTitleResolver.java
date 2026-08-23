package com.moca.mocabe.domain.report.service;

import com.moca.mocabe.domain.report.model.MissedBenefitRow;

/** 혜택 리포트에 표시할 제목의 업무 우선순위를 결정한다. */
public class BenefitReportTitleResolver {

  public String resolve(MissedBenefitRow row) {
    return firstNonBlank(row.reportTitle(), row.benefitTitle(), row.ruleTitle(), row.offerName());
  }

  private String firstNonBlank(String... candidates) {
    for (String candidate : candidates) {
      if (candidate != null && !candidate.trim().isEmpty()) {
        return candidate.trim();
      }
    }
    return null;
  }
}
