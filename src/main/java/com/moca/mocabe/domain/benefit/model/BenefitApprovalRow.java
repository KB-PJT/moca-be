package com.moca.mocabe.domain.benefit.model;

import java.time.LocalDateTime;

/** 새로 적재된 승인과 혜택 대상 판정에 필요한 가맹점 카테고리다. */
public record BenefitApprovalRow(
    String approvalId,
    String userCardId,
    int amount,
    LocalDateTime approvedAt,
    String merchantCategoryCode,
    String merchantId,
    String merchantCategoryCodes,
    String merchantCategoryIds) {

  public BenefitApprovalRow(String approvalId, String userCardId, int amount,
                            LocalDateTime approvedAt, String merchantCategoryCode) {
    this(approvalId, userCardId, amount, approvedAt, merchantCategoryCode, null,
        merchantCategoryCode, null);
  }
}
