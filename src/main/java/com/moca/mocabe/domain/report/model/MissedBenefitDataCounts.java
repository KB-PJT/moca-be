package com.moca.mocabe.domain.report.model;

/** 미사용 혜택 리포트의 원천 데이터 존재 여부를 확인하기 위한 월별 건수다. */
public record MissedBenefitDataCounts(long approvalCount, long outcomeCount, long usageCount) {
  public static MissedBenefitDataCounts empty() {
    return new MissedBenefitDataCounts(0, 0, 0);
  }
}
