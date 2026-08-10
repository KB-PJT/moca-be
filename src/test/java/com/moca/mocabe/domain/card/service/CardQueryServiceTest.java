package com.moca.mocabe.domain.card.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moca.mocabe.domain.card.dto.CardDetailResponse;
import com.moca.mocabe.domain.card.dto.MeCardItemResponse;
import com.moca.mocabe.domain.card.dto.MeCardsResponse;
import com.moca.mocabe.domain.card.exception.InvalidCardOrderException;
import com.moca.mocabe.domain.card.mapper.CardBenefitMapper;
import com.moca.mocabe.domain.card.mapper.UserCardMapper;
import com.moca.mocabe.domain.card.model.CardBenefitRow;
import com.moca.mocabe.domain.card.model.UserCardListRow;
import com.moca.mocabe.domain.codef.exception.UserCardNotFoundException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CardQueryServiceTest {

    private static final String USER_ID = "01980d6a-5c0c-7aaf-9b85-010203040506";
    private static final String ACTIVE_USER_CARD_ID = "01980d6a-5c0c-7aaf-9b85-010203040531";
    private static final String OTHER_ACTIVE_USER_CARD_ID = "01980d6a-5c0c-7aaf-9b85-010203040532";
    private static final String INACTIVE_USER_CARD_ID = "01980d6a-5c0c-7aaf-9b85-010203040533";
    private static final String ISSUER_ID = "00000000-0000-4000-8000-000000000301";

    private static final String CONTENT_VERSION_ID = "01980d6a-5c0c-7aaf-9b85-010203040540";

    @Mock
    private UserCardMapper userCardMapper;

    @Mock
    private CardBenefitMapper cardBenefitMapper;

    private CardQueryService cardQueryService;

    @BeforeEach
    void setUp() {
        cardQueryService = new CardQueryService(userCardMapper, cardBenefitMapper);
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

    @Test
    @DisplayName("본인 소유 카드면 메모를 수정하고 갱신된 카드 정보를 반환한다")
    void updatesMemoForOwnedCard() {
        when(userCardMapper.updateMemo(ACTIVE_USER_CARD_ID, USER_ID, "새 메모")).thenReturn(1);
        when(userCardMapper.findByUserCardId(ACTIVE_USER_CARD_ID, USER_ID))
                .thenReturn(userCard(ACTIVE_USER_CARD_ID, "KB My WE:SH", null, "새 메모"));

        MeCardItemResponse response = cardQueryService.updateMemo(USER_ID, ACTIVE_USER_CARD_ID, "새 메모");

        assertEquals(ACTIVE_USER_CARD_ID, response.getUserCardId());
        assertEquals("새 메모", response.getMemo());
    }

    @Test
    @DisplayName("메모를 null로 보내면 그대로 반영한다")
    void clearsMemoWhenNull() {
        when(userCardMapper.updateMemo(ACTIVE_USER_CARD_ID, USER_ID, null)).thenReturn(1);
        when(userCardMapper.findByUserCardId(ACTIVE_USER_CARD_ID, USER_ID))
                .thenReturn(userCard(ACTIVE_USER_CARD_ID, "KB My WE:SH", null, null));

        MeCardItemResponse response = cardQueryService.updateMemo(USER_ID, ACTIVE_USER_CARD_ID, null);

        assertNull(response.getMemo());
    }

    @Test
    @DisplayName("본인 소유 카드가 아니면 예외를 던지고 갱신된 정보를 다시 조회하지 않는다")
    void rejectsMemoUpdateForUnknownCard() {
        when(userCardMapper.updateMemo(ACTIVE_USER_CARD_ID, USER_ID, "메모")).thenReturn(0);

        assertThrows(UserCardNotFoundException.class,
                () -> cardQueryService.updateMemo(USER_ID, ACTIVE_USER_CARD_ID, "메모"));

        verify(userCardMapper, never()).findByUserCardId(ACTIVE_USER_CARD_ID, USER_ID);
    }

    @Test
    @DisplayName("본인 소유 카드면 혜택을 유형별로 나눠 상세정보를 반환한다")
    void returnsCardDetailSplitByRecordType() {
        UserCardListRow cardRow = userCard(ACTIVE_USER_CARD_ID, "KB My WE:SH",
                "https://example.com/card.png", "배달 귀요미 카드");
        cardRow.setContentVersionId(CONTENT_VERSION_ID);
        when(userCardMapper.findByUserCardId(ACTIVE_USER_CARD_ID, USER_ID)).thenReturn(cardRow);
        when(cardBenefitMapper.findByContentVersionId(CONTENT_VERSION_ID)).thenReturn(List.of(
                benefitRow("benefit", "카페 10% 할인", "월 최대 5,000원", "카페 상세", "<p>카페 상세</p>"),
                benefitRow("notice", "할인서비스 적용 안내", null, "유의사항 상세", "<p>유의사항 상세</p>")));

        CardDetailResponse response = cardQueryService.getCardDetail(USER_ID, ACTIVE_USER_CARD_ID);

        assertEquals(ACTIVE_USER_CARD_ID, response.getUserCardId());
        assertEquals("배달 귀요미 카드", response.getMemo());
        assertEquals(1, response.getBenefits().size());
        assertEquals("카페 10% 할인", response.getBenefits().get(0).getTitle());
        assertEquals("카페 상세", response.getBenefits().get(0).getDetailText());
        assertEquals("<p>카페 상세</p>", response.getBenefits().get(0).getDetailHtml());
        assertEquals(1, response.getNotices().size());
        assertEquals("할인서비스 적용 안내", response.getNotices().get(0).getTitle());
    }

    @Test
    @DisplayName("카드가 카탈로그와 매칭되지 않으면 혜택 조회 없이 빈 목록을 반환한다")
    void returnsEmptyBenefitsWhenNoContentVersion() {
        UserCardListRow cardRow = userCard(ACTIVE_USER_CARD_ID, "KB My WE:SH", null, null);
        when(userCardMapper.findByUserCardId(ACTIVE_USER_CARD_ID, USER_ID)).thenReturn(cardRow);

        CardDetailResponse response = cardQueryService.getCardDetail(USER_ID, ACTIVE_USER_CARD_ID);

        assertEquals(List.of(), response.getBenefits());
        assertEquals(List.of(), response.getNotices());
        verify(cardBenefitMapper, never()).findByContentVersionId(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("본인 소유 카드가 아니면 예외를 던진다")
    void rejectsDetailForUnknownCard() {
        when(userCardMapper.findByUserCardId(ACTIVE_USER_CARD_ID, USER_ID)).thenReturn(null);

        assertThrows(UserCardNotFoundException.class,
                () -> cardQueryService.getCardDetail(USER_ID, ACTIVE_USER_CARD_ID));
    }

    @Test
    @DisplayName("요청한 순서가 활성 카드 전체와 일치하면 순서를 저장하고 갱신된 목록을 반환한다")
    void reordersOwnedActiveCards() {
        List<String> newOrder = List.of(OTHER_ACTIVE_USER_CARD_ID, ACTIVE_USER_CARD_ID);
        when(userCardMapper.findActiveByUserId(USER_ID))
                .thenReturn(List.of(
                        userCard(ACTIVE_USER_CARD_ID, "KB My WE:SH", null, null),
                        userCard(OTHER_ACTIVE_USER_CARD_ID, "KB 국민 일반", null, null)))
                .thenReturn(List.of(
                        userCard(OTHER_ACTIVE_USER_CARD_ID, "KB 국민 일반", null, null),
                        userCard(ACTIVE_USER_CARD_ID, "KB My WE:SH", null, null)));
        when(userCardMapper.findInactiveByUserId(USER_ID)).thenReturn(List.of());

        MeCardsResponse response = cardQueryService.reorderCards(USER_ID, newOrder);

        verify(userCardMapper).updateDisplayOrders(USER_ID, newOrder);
        assertEquals(OTHER_ACTIVE_USER_CARD_ID, response.getActiveCards().get(0).getUserCardId());
        assertEquals(ACTIVE_USER_CARD_ID, response.getActiveCards().get(1).getUserCardId());
    }

    @Test
    @DisplayName("요청한 카드 목록이 활성 카드 전체와 다르면 저장하지 않고 예외를 던진다")
    void rejectsReorderWhenCardSetMismatches() {
        when(userCardMapper.findActiveByUserId(USER_ID)).thenReturn(List.of(
                userCard(ACTIVE_USER_CARD_ID, "KB My WE:SH", null, null),
                userCard(OTHER_ACTIVE_USER_CARD_ID, "KB 국민 일반", null, null)));

        assertThrows(InvalidCardOrderException.class,
                () -> cardQueryService.reorderCards(USER_ID, List.of(ACTIVE_USER_CARD_ID)));

        verify(userCardMapper, never()).updateDisplayOrders(org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("요청한 카드 목록에 중복이 있으면 저장하지 않고 예외를 던진다")
    void rejectsReorderWhenDuplicateIdsProvided() {
        when(userCardMapper.findActiveByUserId(USER_ID)).thenReturn(List.of(
                userCard(ACTIVE_USER_CARD_ID, "KB My WE:SH", null, null),
                userCard(OTHER_ACTIVE_USER_CARD_ID, "KB 국민 일반", null, null)));

        assertThrows(InvalidCardOrderException.class,
                () -> cardQueryService.reorderCards(USER_ID,
                        List.of(ACTIVE_USER_CARD_ID, ACTIVE_USER_CARD_ID)));

        verify(userCardMapper, never()).updateDisplayOrders(org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("본인 소유 카드면 is_active를 false로 변경한다")
    void deactivatesOwnedCard() {
        when(userCardMapper.deactivateUserCard(ACTIVE_USER_CARD_ID, USER_ID)).thenReturn(1);

        cardQueryService.deactivateCard(USER_ID, ACTIVE_USER_CARD_ID);

        verify(userCardMapper).deactivateUserCard(ACTIVE_USER_CARD_ID, USER_ID);
    }

    @Test
    @DisplayName("본인 소유 카드가 아니면 비활성화 시 예외를 던진다")
    void rejectsDeactivateForUnknownCard() {
        when(userCardMapper.deactivateUserCard(ACTIVE_USER_CARD_ID, USER_ID)).thenReturn(0);

        assertThrows(UserCardNotFoundException.class,
                () -> cardQueryService.deactivateCard(USER_ID, ACTIVE_USER_CARD_ID));
    }

    @Test
    @DisplayName("본인 소유 카드면 자식 테이블을 먼저 지우고 user_cards를 삭제한다")
    void disconnectsOwnedCardByDeletingChildRowsFirst() {
        when(userCardMapper.findByUserCardId(ACTIVE_USER_CARD_ID, USER_ID))
                .thenReturn(userCard(ACTIVE_USER_CARD_ID, "KB My WE:SH", null, null));
        when(userCardMapper.deleteUserCard(ACTIVE_USER_CARD_ID, USER_ID)).thenReturn(1);

        cardQueryService.disconnectCard(USER_ID, ACTIVE_USER_CARD_ID);

        InOrder inOrder = inOrder(userCardMapper);
        inOrder.verify(userCardMapper).deleteBenefitCalculationOutcomesByUserCardId(ACTIVE_USER_CARD_ID);
        inOrder.verify(userCardMapper).deleteBenefitUsagesByUserCardId(ACTIVE_USER_CARD_ID);
        inOrder.verify(userCardMapper).deleteOptionSelectionsByUserCardId(ACTIVE_USER_CARD_ID);
        inOrder.verify(userCardMapper).deletePerformanceSnapshotsByUserCardId(ACTIVE_USER_CARD_ID);
        inOrder.verify(userCardMapper).deletePaymentApprovalsByUserCardId(ACTIVE_USER_CARD_ID);
        inOrder.verify(userCardMapper).deleteUserCard(ACTIVE_USER_CARD_ID, USER_ID);
    }

    @Test
    @DisplayName("본인 소유 카드가 아니면 자식 테이블을 지우지 않고 예외를 던진다")
    void rejectsDisconnectForUnknownCard() {
        when(userCardMapper.findByUserCardId(ACTIVE_USER_CARD_ID, USER_ID)).thenReturn(null);

        assertThrows(UserCardNotFoundException.class,
                () -> cardQueryService.disconnectCard(USER_ID, ACTIVE_USER_CARD_ID));

        verify(userCardMapper, never()).deleteBenefitCalculationOutcomesByUserCardId(ACTIVE_USER_CARD_ID);
        verify(userCardMapper, never()).deleteUserCard(ACTIVE_USER_CARD_ID, USER_ID);
    }

    @Test
    @DisplayName("삭제 직전 다른 요청이 먼저 카드를 지웠으면 예외를 던진다")
    void rejectsDisconnectWhenUserCardAlreadyDeletedConcurrently() {
        when(userCardMapper.findByUserCardId(ACTIVE_USER_CARD_ID, USER_ID))
                .thenReturn(userCard(ACTIVE_USER_CARD_ID, "KB My WE:SH", null, null));
        when(userCardMapper.deleteUserCard(ACTIVE_USER_CARD_ID, USER_ID)).thenReturn(0);

        assertThrows(UserCardNotFoundException.class,
                () -> cardQueryService.disconnectCard(USER_ID, ACTIVE_USER_CARD_ID));
    }

    private CardBenefitRow benefitRow(
            String recordType,
            String title,
            String summary,
            String detailText,
            String detailHtml
    ) {
        CardBenefitRow row = new CardBenefitRow();
        row.setBenefitId("01980d6a-5c0c-7aaf-9b85-010203040550");
        row.setRecordType(recordType);
        row.setTitle(title);
        row.setSummary(summary);
        row.setDetailText(detailText);
        row.setDetailHtml(detailHtml);
        return row;
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
