package com.moca.mocabe.domain.benefit.dto;

public class BenefitHistoryMetaResponse {
  private final int page;
  private final int size;
  private final long totalCount;
  private final boolean hasNext;

  public BenefitHistoryMetaResponse(int page, int size, long totalCount, boolean hasNext) {
    this.page = page;
    this.size = size;
    this.totalCount = totalCount;
    this.hasNext = hasNext;
  }

  public int getPage() {
    return page;
  }

  public int getSize() {
    return size;
  }

  public long getTotalCount() {
    return totalCount;
  }

  public boolean isHasNext() {
    return hasNext;
  }
}
