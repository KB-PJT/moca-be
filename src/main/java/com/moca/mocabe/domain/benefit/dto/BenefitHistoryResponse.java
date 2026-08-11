package com.moca.mocabe.domain.benefit.dto;

import java.util.List;

public class BenefitHistoryResponse {
  private final List<BenefitHistoryItemResponse> data;
  private final BenefitHistorySummaryResponse summary;
  private final BenefitHistoryMetaResponse meta;

  public BenefitHistoryResponse(
      List<BenefitHistoryItemResponse> data,
      BenefitHistorySummaryResponse summary,
      BenefitHistoryMetaResponse meta) {
    this.data = data;
    this.summary = summary;
    this.meta = meta;
  }

  public List<BenefitHistoryItemResponse> getData() {
    return data;
  }

  public BenefitHistorySummaryResponse getSummary() {
    return summary;
  }

  public BenefitHistoryMetaResponse getMeta() {
    return meta;
  }
}
