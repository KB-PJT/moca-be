package com.moca.mocabe.domain.card.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class UserCardListRowTest {

    private static final String USER_CARD_ID = "01980d6a-5c0c-7aaf-9b85-010203040531";
    private static final String ISSUER_ID = "00000000-0000-4000-8000-000000000301";

    @Test
    void storesJoinedCardListFields() {
        UserCardListRow cardRow = new UserCardListRow();
        cardRow.setUserCardId(USER_CARD_ID);
        cardRow.setCardName("KB My WE:SH");
        cardRow.setIssuerId(ISSUER_ID);
        cardRow.setIssuerName("KB카드");
        cardRow.setCardImageUrl(null);
        cardRow.setMemo("카페 전용 카드");

        assertEquals(USER_CARD_ID, cardRow.getUserCardId());
        assertEquals("KB My WE:SH", cardRow.getCardName());
        assertEquals(ISSUER_ID, cardRow.getIssuerId());
        assertEquals("KB카드", cardRow.getIssuerName());
        assertNull(cardRow.getCardImageUrl());
        assertEquals("카페 전용 카드", cardRow.getMemo());
    }
}
