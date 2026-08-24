package com.moca.mocabe.domain.home.model;

import java.time.LocalDateTime;

/** 최근 결제 승인 한 건과 혜택 계산 요약의 MyBatis 조회 결과다. */
public class RecentHistoryRow {

  private String approvalId;
  private String benefitHistoryId;
  private String merchantName;
  private String benefitType;
  private String benefitTitle;
  private String cardName;
  private long paymentAmount;
  private long benefitAmount;
  private long missedBenefitAmount;
  private String calculationStatus;
  private String rejectionReason;
  private LocalDateTime occurredAt;
  private Long requiredPreviousSpendAmount;
  private Long previousMonthSpendAmount;
  private long monthlyUsedAmount;
  private long monthlyLimitAmount;

  public String getApprovalId() {
    return approvalId;
  }

  public void setApprovalId(String approvalId) {
    this.approvalId = approvalId;
  }

  public String getBenefitHistoryId() {
    return benefitHistoryId;
  }

  public void setBenefitHistoryId(String benefitHistoryId) {
    this.benefitHistoryId = benefitHistoryId;
  }

  public String getMerchantName() {
    return merchantName;
  }

  public void setMerchantName(String merchantName) {
    this.merchantName = merchantName;
  }

  public String getBenefitType() {
    return benefitType;
  }

  public void setBenefitType(String benefitType) {
    this.benefitType = benefitType;
  }

  public String getBenefitTitle() {
    return benefitTitle;
  }

  public void setBenefitTitle(String benefitTitle) {
    this.benefitTitle = benefitTitle;
  }

  public String getCardName() {
    return cardName;
  }

  public void setCardName(String cardName) {
    this.cardName = cardName;
  }

  public long getPaymentAmount() {
    return paymentAmount;
  }

  public void setPaymentAmount(long paymentAmount) {
    this.paymentAmount = paymentAmount;
  }

  public long getBenefitAmount() {
    return benefitAmount;
  }

  public void setBenefitAmount(long benefitAmount) {
    this.benefitAmount = benefitAmount;
  }

  public long getMissedBenefitAmount() {
    return missedBenefitAmount;
  }

  public void setMissedBenefitAmount(long missedBenefitAmount) {
    this.missedBenefitAmount = missedBenefitAmount;
  }

  public String getCalculationStatus() {
    return calculationStatus;
  }

  public void setCalculationStatus(String calculationStatus) {
    this.calculationStatus = calculationStatus;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  public void setRejectionReason(String rejectionReason) {
    this.rejectionReason = rejectionReason;
  }

  public LocalDateTime getOccurredAt() {
    return occurredAt;
  }

  public void setOccurredAt(LocalDateTime occurredAt) {
    this.occurredAt = occurredAt;
  }

  public Long getRequiredPreviousSpendAmount() {
    return requiredPreviousSpendAmount;
  }

  public void setRequiredPreviousSpendAmount(Long requiredPreviousSpendAmount) {
    this.requiredPreviousSpendAmount = requiredPreviousSpendAmount;
  }

  public Long getPreviousMonthSpendAmount() {
    return previousMonthSpendAmount;
  }

  public void setPreviousMonthSpendAmount(Long previousMonthSpendAmount) {
    this.previousMonthSpendAmount = previousMonthSpendAmount;
  }

  public long getMonthlyUsedAmount() {
    return monthlyUsedAmount;
  }

  public void setMonthlyUsedAmount(long monthlyUsedAmount) {
    this.monthlyUsedAmount = monthlyUsedAmount;
  }

  public long getMonthlyLimitAmount() {
    return monthlyLimitAmount;
  }

  public void setMonthlyLimitAmount(long monthlyLimitAmount) {
    this.monthlyLimitAmount = monthlyLimitAmount;
  }
}
