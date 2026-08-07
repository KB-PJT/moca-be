package com.moca.mocabe.domain.home.model;

import java.time.LocalDateTime;

/** 최근 확정 혜택 한 건의 MyBatis 조회 결과다. */
public class RecentBenefitRow {

  private String benefitHistoryId;
  private String merchantName;
  private String benefitType;
  private String benefitTitle;
  private String cardName;
  private long paymentAmount;
  private long benefitAmount;
  private LocalDateTime occurredAt;

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

  public LocalDateTime getOccurredAt() {
    return occurredAt;
  }

  public void setOccurredAt(LocalDateTime occurredAt) {
    this.occurredAt = occurredAt;
  }
}
