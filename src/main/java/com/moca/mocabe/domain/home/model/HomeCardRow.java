package com.moca.mocabe.domain.home.model;

/** 홈 카드 캐러셀 집계용 MyBatis 조회 결과다. */
public class HomeCardRow {

  private String userCardId;
  private String cardName;
  private String alias;
  private String cardImageUrl;
  private String highlightBenefitTitle;
  private long maximumMonthlyBenefitAmount;
  private long receivedBenefitAmount;
  private long performanceCurrentAmount;
  private long performanceTargetAmount;
  private int displayOrder;

  public String getUserCardId() {
    return userCardId;
  }

  public void setUserCardId(String userCardId) {
    this.userCardId = userCardId;
  }

  public String getCardName() {
    return cardName;
  }

  public void setCardName(String cardName) {
    this.cardName = cardName;
  }

  public String getAlias() {
    return alias;
  }

  public void setAlias(String alias) {
    this.alias = alias;
  }

  public String getCardImageUrl() {
    return cardImageUrl;
  }

  public void setCardImageUrl(String cardImageUrl) {
    this.cardImageUrl = cardImageUrl;
  }

  public String getHighlightBenefitTitle() {
    return highlightBenefitTitle;
  }

  public void setHighlightBenefitTitle(String highlightBenefitTitle) {
    this.highlightBenefitTitle = highlightBenefitTitle;
  }

  public long getMaximumMonthlyBenefitAmount() {
    return maximumMonthlyBenefitAmount;
  }

  public void setMaximumMonthlyBenefitAmount(long maximumMonthlyBenefitAmount) {
    this.maximumMonthlyBenefitAmount = maximumMonthlyBenefitAmount;
  }

  public long getReceivedBenefitAmount() {
    return receivedBenefitAmount;
  }

  public void setReceivedBenefitAmount(long receivedBenefitAmount) {
    this.receivedBenefitAmount = receivedBenefitAmount;
  }

  public long getPerformanceCurrentAmount() {
    return performanceCurrentAmount;
  }

  public void setPerformanceCurrentAmount(long performanceCurrentAmount) {
    this.performanceCurrentAmount = performanceCurrentAmount;
  }

  public long getPerformanceTargetAmount() {
    return performanceTargetAmount;
  }

  public void setPerformanceTargetAmount(long performanceTargetAmount) {
    this.performanceTargetAmount = performanceTargetAmount;
  }

  public int getDisplayOrder() {
    return displayOrder;
  }

  public void setDisplayOrder(int displayOrder) {
    this.displayOrder = displayOrder;
  }
}
