package com.moca.mocabe.domain.benefit.dto;

import java.util.List;

public class BenefitHistoryResponse {
  private final List<BenefitHistoryItemResponse> data;
  private final BenefitHistoryMetaResponse meta;

  public BenefitHistoryResponse(
      List<BenefitHistoryItemResponse> data, BenefitHistoryMetaResponse meta) {
    this.data = data;
    this.meta = meta;
  }

  public List<BenefitHistoryItemResponse> getData() {
    return data;
  }

  public BenefitHistoryMetaResponse getMeta() {
    return meta;
  }
}
