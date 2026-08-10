package com.moca.mocabe.domain.card.dto;

import com.moca.mocabe.domain.card.model.CardBenefitRow;

/** 카드 상세정보에 포함되는 혜택 또는 유의사항 한 건을 나타낸다. */
public class CardBenefitResponse {

    private final String benefitId;
    private final String title;
    private final String summary;
    private final String detailText;
    private final String detailHtml;

    public CardBenefitResponse(CardBenefitRow benefitRow) {
        this.benefitId = benefitRow.getBenefitId();
        this.title = benefitRow.getTitle();
        this.summary = benefitRow.getSummary();
        this.detailText = benefitRow.getDetailText();
        this.detailHtml = benefitRow.getDetailHtml();
    }

    public String getBenefitId() {
        return benefitId;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getDetailText() {
        return detailText;
    }

    public String getDetailHtml() {
        return detailHtml;
    }
}
