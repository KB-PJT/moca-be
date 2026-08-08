package com.moca.mocabe.domain.card.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moca.mocabe.domain.card.dto.MeCardItemResponse;
import com.moca.mocabe.domain.card.dto.MeCardsResponse;
import com.moca.mocabe.domain.card.mapper.UserCardMapper;
import com.moca.mocabe.domain.card.model.UserCardListRow;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CardQueryServiceTest {

    private static final String USER_ID = "01980d6a-5c0c-7aaf-9b85-010203040506";
    private static final String ACTIVE_USER_CARD_ID = "01980d6a-5c0c-7aaf-9b85-010203040531";
    private static final String INACTIVE_USER_CARD_ID = "01980d6a-5c0c-7aaf-9b85-010203040533";
    private static final String ISSUER_ID = "00000000-0000-4000-8000-000000000301";

    @Mock
    private UserCardMapper userCardMapper;

    private CardQueryService cardQueryService;

    @BeforeEach
    void setUp() {
        cardQueryService = new CardQueryService(userCardMapper);
    }

    @Test
    @DisplayName("기본 조회는 활성 카드와 비활성 카드를 함께 반환한다")
    void returnsActiveAndInactiveCards() {
        when(userCardMapper.findActiveByUserId(USER_ID)).thenReturn(List.of(
                userCard(ACTIVE_USER_CARD_ID, "KB My WE:SH",
                        "https://example.com/card.png", "카페 전용 카드")));
        when(userCardMapper.findInactiveByUserId(USER_ID)).thenReturn(List.of(
                userCard(INACTIVE_USER_CARD_ID, "KB 국민 일반", null, null)));

        MeCardsResponse response = cardQueryService.getMyCards(USER_ID, false);

        assertNull(response.getLastSyncedAt());
        assertCard(response.getActiveCards().get(0), ACTIVE_USER_CARD_ID, "KB My WE:SH",
                "https://example.com/card.png", "카페 전용 카드");
        assertCard(response.getInactiveCards().get(0), INACTIVE_USER_CARD_ID, "KB 국민 일반", null, null);
        verify(userCardMapper).findActiveByUserId(USER_ID);
        verify(userCardMapper).findInactiveByUserId(USER_ID);
    }

    @Test
    @DisplayName("활성 카드 전용 조회는 비활성 카드 쿼리를 실행하지 않는다")
    void skipsInactiveCardsWhenActiveOnly() {
        when(userCardMapper.findActiveByUserId(USER_ID)).thenReturn(List.of());

        MeCardsResponse response = cardQueryService.getMyCards(USER_ID, true);

        assertEquals(List.of(), response.getActiveCards());
        assertNull(response.getInactiveCards());
        verify(userCardMapper, never()).findInactiveByUserId(USER_ID);
    }

    @Test
    @DisplayName("활성·비활성 상관없이 보유 카드가 있으면 true를 반환한다")
    void hasAnyCardReturnsMapperResult() {
        when(userCardMapper.existsByUserId(USER_ID)).thenReturn(true);

        org.junit.jupiter.api.Assertions.assertTrue(cardQueryService.hasAnyCard(USER_ID));
        verify(userCardMapper).existsByUserId(USER_ID);
    }

    @Test
    @DisplayName("보유 카드가 없으면 빈 활성·비활성 목록을 반환한다")
    void returnsEmptyCardLists() {
        when(userCardMapper.findActiveByUserId(USER_ID)).thenReturn(List.of());
        when(userCardMapper.findInactiveByUserId(USER_ID)).thenReturn(List.of());

        MeCardsResponse response = cardQueryService.getMyCards(USER_ID, false);

        assertEquals(List.of(), response.getActiveCards());
        assertEquals(List.of(), response.getInactiveCards());
    }

    private UserCardListRow userCard(
            String userCardId,
            String cardName,
            String cardImageUrl,
            String memo
    ) {
        UserCardListRow cardRow = new UserCardListRow();
        cardRow.setUserCardId(userCardId);
        cardRow.setCardName(cardName);
        cardRow.setIssuerId(ISSUER_ID);
        cardRow.setIssuerName("KB카드");
        cardRow.setCardImageUrl(cardImageUrl);
        cardRow.setMemo(memo);
        return cardRow;
    }

    private void assertCard(
            MeCardItemResponse card,
            String userCardId,
            String cardName,
            String cardImageUrl,
            String memo
    ) {
        assertEquals(userCardId, card.getUserCardId());
        assertEquals(cardName, card.getCardName());
        assertEquals(ISSUER_ID, card.getIssuerId());
        assertEquals("KB카드", card.getIssuerName());
        assertEquals(cardImageUrl, card.getCardImageUrl());
        assertEquals(memo, card.getMemo());
    }
}
