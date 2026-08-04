package com.moca.mocabe.domain.home.dto;

/** 홈 최근 혜택 내역 한 건이다. */
public class RecentBenefitItemResponse {

    private final String benefitHistoryId;
    private final String merchantName;
    private final String benefitType;
    private final String benefitTitle;
    private final String cardName;
    private final long paymentAmount;
    private final long benefitAmount;
    private final String occurredAt;

    public RecentBenefitItemResponse(String benefitHistoryId, String merchantName, String benefitType,
                                     String benefitTitle, String cardName, long paymentAmount,
                                     long benefitAmount, String occurredAt) {
        this.benefitHistoryId = benefitHistoryId;
        this.merchantName = merchantName;
        this.benefitType = benefitType;
        this.benefitTitle = benefitTitle;
        this.cardName = cardName;
        this.paymentAmount = paymentAmount;
        this.benefitAmount = benefitAmount;
        this.occurredAt = occurredAt;
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

    public String getOccurredAt() {
        return occurredAt;
    }
}
