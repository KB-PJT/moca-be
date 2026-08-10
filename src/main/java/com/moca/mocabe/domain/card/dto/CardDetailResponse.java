package com.moca.mocabe.domain.card.dto;

import com.moca.mocabe.domain.card.model.UserCardListRow;
import java.util.List;

/** 보유 카드 상세정보(카드 정보·메모·혜택·유의사항)를 나타낸다. */
public class CardDetailResponse {

    private final String userCardId;
    private final String cardName;
    private final String cardNo;
    private final String issuerId;
    private final String issuerName;
    private final String cardImageUrl;
    private final String memo;
    private final List<CardBenefitResponse> benefits;
    private final List<CardBenefitResponse> notices;

    public CardDetailResponse(UserCardListRow cardRow, List<CardBenefitResponse> benefits,
                              List<CardBenefitResponse> notices) {
        this.userCardId = cardRow.getUserCardId();
        this.cardName = cardRow.getCardName();
        this.cardNo = cardRow.getCardNo();
        this.issuerId = cardRow.getIssuerId();
        this.issuerName = cardRow.getIssuerName();
        this.cardImageUrl = cardRow.getCardImageUrl();
        this.memo = cardRow.getMemo();
        this.benefits = benefits;
        this.notices = notices;
    }

    public String getUserCardId() {
        return userCardId;
    }

    public String getCardName() {
        return cardName;
    }

    public String getCardNo() {
        return cardNo;
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

    public List<CardBenefitResponse> getBenefits() {
        return benefits;
    }

    public List<CardBenefitResponse> getNotices() {
        return notices;
    }
}
