package com.moca.mocabe.domain.home.dto;

import com.moca.mocabe.domain.benefit.dto.PerformanceShortfallResponse;

/** 홈에 표시할 최근 결제 내역 한 건이다. */
public class RecentHistoryItemResponse {

  private final String approvalId;
  private final String benefitHistoryId;
  private final String merchantName;
  private final String benefitType;
  private final String benefitTitle;
  private final String cardName;
  private final long paymentAmount;
  private final long benefitAmount;
  private final long missedBenefitAmount;
  private final String calculationStatus;
  private final String rejectionReason;
  private final String occurredAt;
  private final PerformanceShortfallResponse performanceShortfall;
  private final Long monthlyBenefitUsed;
  private final Long monthlyBenefitLimit;

  public RecentHistoryItemResponse(
      String approvalId,
      String benefitHistoryId,
      String merchantName,
      String benefitType,
      String benefitTitle,
      String cardName,
      long paymentAmount,
      long benefitAmount,
      long missedBenefitAmount,
      String calculationStatus,
      String rejectionReason,
      String occurredAt,
      PerformanceShortfallResponse performanceShortfall,
      Long monthlyBenefitUsed,
      Long monthlyBenefitLimit) {
    this.approvalId = approvalId;
    this.benefitHistoryId = benefitHistoryId;
    this.merchantName = merchantName;
    this.benefitType = benefitType;
    this.benefitTitle = benefitTitle;
    this.cardName = cardName;
    this.paymentAmount = paymentAmount;
    this.benefitAmount = benefitAmount;
    this.missedBenefitAmount = missedBenefitAmount;
    this.calculationStatus = calculationStatus;
    this.rejectionReason = rejectionReason;
    this.occurredAt = occurredAt;
    this.performanceShortfall = performanceShortfall;
    this.monthlyBenefitUsed = monthlyBenefitUsed;
    this.monthlyBenefitLimit = monthlyBenefitLimit;
  }

  public String getApprovalId() {
    return approvalId;
  }

  public String getBenefitHistoryId() {
    return benefitHistoryId;
  }

  public String getMerchantName() {
    return merchantName;
  }

  public String getBenefitType() {
    return benefitType;
  }

  public String getBenefitTitle() {
    return benefitTitle;
  }

  public String getCardName() {
    return cardName;
  }

  public long getPaymentAmount() {
    return paymentAmount;
  }

  public long getBenefitAmount() {
    return benefitAmount;
  }

  public long getMissedBenefitAmount() {
    return missedBenefitAmount;
  }

  public String getCalculationStatus() {
    return calculationStatus;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  public String getOccurredAt() {
    return occurredAt;
  }

  public PerformanceShortfallResponse getPerformanceShortfall() {
    return performanceShortfall;
  }

  public Long getMonthlyBenefitUsed() {
    return monthlyBenefitUsed;
  }

  public Long getMonthlyBenefitLimit() {
    return monthlyBenefitLimit;
  }
}
