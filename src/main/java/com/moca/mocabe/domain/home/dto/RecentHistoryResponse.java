package com.moca.mocabe.domain.home.dto;

import java.util.List;

/** 홈 최근 결제 내역 목록이다. */
public class RecentHistoryResponse {

  private final List<RecentHistoryItemResponse> history;

  public RecentHistoryResponse(List<RecentHistoryItemResponse> history) {
    this.history = history;
  }

  public List<RecentHistoryItemResponse> getHistory() {
    return history;
  }
}
