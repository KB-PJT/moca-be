package com.moca.mocabe.domain.benefit.dto;

public class BenefitHistoryDetailResponse {
  private final String benefitHistoryId,
      calculationStatus,
      merchantName,
      approvedAt,
      cardName,
      benefitType,
      benefitTitle;
  private final long paymentAmount, benefitAmount;
  private final MonthlyLimitResponse monthlyLimit;
  private final Long earnedMileage;
  private final long missedBenefitAmount;
  private final String rejectionReason;
  private final PerformanceShortfallResponse performanceShortfall;

  public BenefitHistoryDetailResponse(
      String id,
      String status,
      String merchant,
      String approved,
      String card,
      long payment,
      long benefit,
      String type,
      String title,
      MonthlyLimitResponse limit,
      Long mileage,
      long missedBenefit,
      String rejectionReason,
      PerformanceShortfallResponse performanceShortfall) {
    benefitHistoryId = id;
    calculationStatus = status;
    merchantName = merchant;
    approvedAt = approved;
    cardName = card;
    paymentAmount = payment;
    benefitAmount = benefit;
    benefitType = type;
    benefitTitle = title;
    monthlyLimit = limit;
    earnedMileage = mileage;
    missedBenefitAmount = missedBenefit;
    this.rejectionReason = rejectionReason;
    this.performanceShortfall = performanceShortfall;
  }

  public String getBenefitHistoryId() {
    return benefitHistoryId;
  }

  public String getCalculationStatus() {
    return calculationStatus;
  }

  public String getMerchantName() {
    return merchantName;
  }

  public String getApprovedAt() {
    return approvedAt;
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

  public String getBenefitType() {
    return benefitType;
  }

  public String getBenefitTitle() {
    return benefitTitle;
  }

  public MonthlyLimitResponse getMonthlyLimit() {
    return monthlyLimit;
  }

  public Long getEarnedMileage() {
    return earnedMileage;
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
