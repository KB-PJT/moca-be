package com.moca.mocabe.domain.benefit.model;

import java.time.LocalDateTime;

/** 혜택 이력 목록 SQL 조회 모델이다. */
public class BenefitHistoryRow {
  private String benefitHistoryId;
  private String approvalId;
  private String merchantName;
  private LocalDateTime approvedAt;
  private long paymentAmount;
  private long benefitAmount;
  private String benefitUnit;
  private String benefitType;
  private String benefitTitle;
  private String userCardId;
  private String cardName;
  private String calculationStatus;
  private long missedBenefitAmount;
  private String rejectionReason;
  private Long requiredPreviousSpendAmount;
  private Long previousMonthSpendAmount;
  private long monthlyUsedAmount;
  private long monthlyLimitAmount;

  public String getBenefitHistoryId() {
    return benefitHistoryId;
  }

  public void setBenefitHistoryId(String value) {
    benefitHistoryId = value;
  }

  public String getApprovalId() {
    return approvalId;
  }

  public void setApprovalId(String value) {
    approvalId = value;
  }

  public String getMerchantName() {
    return merchantName;
  }

  public void setMerchantName(String value) {
    merchantName = value;
  }

  public LocalDateTime getApprovedAt() {
    return approvedAt;
  }

  public void setApprovedAt(LocalDateTime value) {
    approvedAt = value;
  }

  public long getPaymentAmount() {
    return paymentAmount;
  }

  public void setPaymentAmount(long value) {
    paymentAmount = value;
  }

  public long getBenefitAmount() {
    return benefitAmount;
  }

  public void setBenefitAmount(long value) {
    benefitAmount = value;
  }

  public String getBenefitUnit() {
    return benefitUnit;
  }

  public void setBenefitUnit(String value) {
    benefitUnit = value;
  }

  public String getBenefitType() {
    return benefitType;
  }

  public void setBenefitType(String value) {
    benefitType = value;
  }

  public String getBenefitTitle() {
    return benefitTitle;
  }

  public void setBenefitTitle(String value) {
    benefitTitle = value;
  }

  public String getUserCardId() {
    return userCardId;
  }

  public void setUserCardId(String value) {
    userCardId = value;
  }

  public String getCardName() {
    return cardName;
  }

  public void setCardName(String value) {
    cardName = value;
  }

  public String getCalculationStatus() {
    return calculationStatus;
  }

  public void setCalculationStatus(String value) {
    calculationStatus = value;
  }

  public long getMissedBenefitAmount() {
    return missedBenefitAmount;
  }

  public void setMissedBenefitAmount(long value) {
    missedBenefitAmount = value;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  public void setRejectionReason(String value) {
    rejectionReason = value;
  }

  public Long getRequiredPreviousSpendAmount() {
    return requiredPreviousSpendAmount;
  }

  public void setRequiredPreviousSpendAmount(Long value) {
    requiredPreviousSpendAmount = value;
  }

  public Long getPreviousMonthSpendAmount() {
    return previousMonthSpendAmount;
  }

  public void setPreviousMonthSpendAmount(Long value) {
    previousMonthSpendAmount = value;
  }

  public long getMonthlyUsedAmount() {
    return monthlyUsedAmount;
  }

  public void setMonthlyUsedAmount(long value) {
    monthlyUsedAmount = value;
  }

  public long getMonthlyLimitAmount() {
    return monthlyLimitAmount;
  }

  public void setMonthlyLimitAmount(long value) {
    monthlyLimitAmount = value;
  }
}
