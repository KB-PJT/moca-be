package com.moca.mocabe.domain.card.model;

/** 사용자 보유 카드 목록 JOIN 조회 결과를 나타내는 MyBatis 모델이다. */
public class UserCardListRow {

    private String userCardId;
    private String cardName;
    private String cardNo;
    private String issuerId;
    private String issuerName;
    private String cardImageUrl;
    private String memo;
    private String contentVersionId;

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

    public String getCardNo() {
        return cardNo;
    }

    public void setCardNo(String cardNo) {
        this.cardNo = cardNo;
    }

    public String getIssuerId() {
        return issuerId;
    }

    public void setIssuerId(String issuerId) {
        this.issuerId = issuerId;
    }

    public String getIssuerName() {
        return issuerName;
    }

    public void setIssuerName(String issuerName) {
        this.issuerName = issuerName;
    }

    public String getCardImageUrl() {
        return cardImageUrl;
    }

    public void setCardImageUrl(String cardImageUrl) {
        this.cardImageUrl = cardImageUrl;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }

    public String getContentVersionId() {
        return contentVersionId;
    }

    public void setContentVersionId(String contentVersionId) {
        this.contentVersionId = contentVersionId;
    }
}
