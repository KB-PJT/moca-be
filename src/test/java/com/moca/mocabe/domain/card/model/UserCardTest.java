package com.moca.mocabe.domain.card.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class UserCardTest {

    private static final String USER_CARD_ID = "01980d6a-5c0c-7aaf-9b85-010203040531";
    private static final String CODEF_ACCOUNT_CREDENTIAL_ID = "01980d6a-5c0c-7aaf-9b85-010203040521";
    private static final String ISSUER_ID = "00000000-0000-4000-8000-000000000301";

    @Test
    void storesMappedCardFields() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 1, 9, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 7, 30, 12, 30);
        UserCard userCard = new UserCard();
        userCard.setUserCardId(USER_CARD_ID);
        userCard.setUserId("01980d6a-5c0c-7aaf-9b85-010203040506");
        userCard.setCardId(null);
        userCard.setCodefAccountCredentialId(CODEF_ACCOUNT_CREDENTIAL_ID);
        userCard.setIssuerId(ISSUER_ID);
        userCard.setCardNameFromCodef("KB My WE:SH");
        userCard.setDisplayOrder(3);
        userCard.setActive(false);
        userCard.setCodefCardKeyHash("hash");
        userCard.setMemo(null);
        userCard.setCreatedAt(createdAt);
        userCard.setUpdatedAt(updatedAt);

        assertEquals(USER_CARD_ID, userCard.getUserCardId());
        assertEquals("01980d6a-5c0c-7aaf-9b85-010203040506", userCard.getUserId());
        assertNull(userCard.getCardId());
        assertEquals(CODEF_ACCOUNT_CREDENTIAL_ID, userCard.getCodefAccountCredentialId());
        assertEquals(ISSUER_ID, userCard.getIssuerId());
        assertEquals("KB My WE:SH", userCard.getCardNameFromCodef());
        assertEquals(3, userCard.getDisplayOrder());
        assertFalse(userCard.isActive());
        assertEquals("hash", userCard.getCodefCardKeyHash());
        assertNull(userCard.getMemo());
        assertEquals(createdAt, userCard.getCreatedAt());
        assertEquals(updatedAt, userCard.getUpdatedAt());
    }
}
