package com.moca.mocabe.domain.card.dto;

import com.moca.mocabe.domain.card.model.UserCardListRow;

/** 내 카드 목록의 카드 한 건을 나타낸다. */
public class MeCardItemResponse {

    private final String userCardId;
    private final String cardName;
    private final String issuerId;
    private final String issuerName;
    private final String cardImageUrl;
    private final String memo;

    public MeCardItemResponse(UserCardListRow cardRow) {
        this.userCardId = cardRow.getUserCardId();
        this.cardName = cardRow.getCardName();
        this.issuerId = cardRow.getIssuerId();
        this.issuerName = cardRow.getIssuerName();
        this.cardImageUrl = cardRow.getCardImageUrl();
        this.memo = cardRow.getMemo();
    }

    public String getUserCardId() {
        return userCardId;
    }

    public String getCardName() {
        return cardName;
    }

    public String getIssuerId() {
        return issuerId;
    }

    public String getIssuerName() {
        return issuerName;
    }

    public String getCardImageUrl() {
        return cardImageUrl;
    }

    public String getMemo() {
        return memo;
    }
}
