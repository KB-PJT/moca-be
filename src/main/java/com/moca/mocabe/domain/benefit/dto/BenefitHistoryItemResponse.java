package com.moca.mocabe.domain.benefit.dto;

/** 혜택 이력 목록 한 건이다. */
public class BenefitHistoryItemResponse {
  private final String benefitHistoryId;
  private final String merchantName;
  private final String approvedAt;
  private final long paymentAmount;
  private final long benefitAmount;
  private final String benefitType;
  private final String benefitTitle;
  private final String userCardId;
  private final String cardName;
  private final String calculationStatus;
  private final long missedBenefitAmount;
  private final String rejectionReason;
  private final PerformanceShortfallResponse performanceShortfall;

  public BenefitHistoryItemResponse(
      String id,
      String merchant,
      String approvedAt,
      long payment,
      long benefit,
      String type,
      String title,
      String cardId,
      String cardName,
      String status,
      long missedBenefit,
      String rejectionReason,
      PerformanceShortfallResponse performanceShortfall) {
    this.benefitHistoryId = id;
    this.merchantName = merchant;
    this.approvedAt = approvedAt;
    this.paymentAmount = payment;
    this.benefitAmount = benefit;
    this.benefitType = type;
    this.benefitTitle = title;
    this.userCardId = cardId;
    this.cardName = cardName;
    this.calculationStatus = status;
    this.missedBenefitAmount = missedBenefit;
    this.rejectionReason = rejectionReason;
    this.performanceShortfall = performanceShortfall;
  }

  public String getBenefitHistoryId() {
    return benefitHistoryId;
  }

  public String getMerchantName() {
    return merchantName;
  }

  public String getApprovedAt() {
    return approvedAt;
  }

  public long getPaymentAmount() {
    return paymentAmount;
  }

  public long getBenefitAmount() {
    return benefitAmount;
  }

  public String getBenefitType() {
    return benefitType;
  }

  public String getBenefitTitle() {
    return benefitTitle;
  }

  public String getUserCardId() {
    return userCardId;
  }

  public String getCardName() {
    return cardName;
  }

  public String getCalculationStatus() {
    return calculationStatus;
  }

  public long getMissedBenefitAmount() {
    return missedBenefitAmount;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  public PerformanceShortfallResponse getPerformanceShortfall() {
    return performanceShortfall;
  }
}
