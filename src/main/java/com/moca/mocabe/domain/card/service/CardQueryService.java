package com.moca.mocabe.domain.card.service;

import com.moca.mocabe.domain.card.dto.CardBenefitResponse;
import com.moca.mocabe.domain.card.dto.CardDetailResponse;
import com.moca.mocabe.domain.card.dto.MeCardItemResponse;
import com.moca.mocabe.domain.card.dto.MeCardsResponse;
import com.moca.mocabe.domain.card.exception.InvalidCardOrderException;
import com.moca.mocabe.domain.card.mapper.CardBenefitMapper;
import com.moca.mocabe.domain.card.mapper.UserCardMapper;
import com.moca.mocabe.domain.card.model.CardBenefitRow;
import com.moca.mocabe.domain.card.model.UserCardListRow;
import com.moca.mocabe.domain.codef.exception.UserCardNotFoundException;
import com.moca.mocabe.domain.home.service.HomeCardsCache;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;

/** 인증 사용자의 보유 카드 목록·상세 조회 유스케이스를 담당한다. */
public class CardQueryService {

    private final UserCardMapper userCardMapper;
    private final CardBenefitMapper cardBenefitMapper;
    private final HomeCardsCache homeCardsCache;

    public CardQueryService(UserCardMapper userCardMapper, CardBenefitMapper cardBenefitMapper) {
        this(userCardMapper, cardBenefitMapper, null);
    }

    public CardQueryService(
            UserCardMapper userCardMapper, CardBenefitMapper cardBenefitMapper, HomeCardsCache homeCardsCache) {
        this.userCardMapper = userCardMapper;
        this.cardBenefitMapper = cardBenefitMapper;
        this.homeCardsCache = homeCardsCache;
    }

    @Transactional(readOnly = true)
    public boolean hasAnyCard(String userId) {
        return userCardMapper.existsByUserId(userId);
    }

    @Transactional(readOnly = true)
    public MeCardsResponse getMyCards(String userId, boolean activeOnly) {
        List<MeCardItemResponse> activeCards = mapCards(userCardMapper.findActiveByUserId(userId));
        List<MeCardItemResponse> inactiveCards = activeOnly
                ? null
                : mapCards(userCardMapper.findInactiveByUserId(userId));

        // CODEF 동기화 시각의 제공 계약이 확정될 때까지 null을 반환한다.
        return new MeCardsResponse(null, activeCards, inactiveCards);
    }

    @Transactional
    public MeCardItemResponse updateMemo(String userId, String userCardId, String memo) {
        if (userCardMapper.updateMemo(userCardId, userId, memo) == 0) {
            throw new UserCardNotFoundException();
        }
        evictHomeCardsCache(userId);
        return new MeCardItemResponse(userCardMapper.findByUserCardId(userCardId, userId));
    }

    @Transactional
    public void deactivateCard(String userId, String userCardId) {
        if (userCardMapper.deactivateUserCard(userCardId, userId) == 0) {
            throw new UserCardNotFoundException();
        }
        evictHomeCardsCache(userId);
    }

    @Transactional
    public MeCardsResponse reorderCards(String userId, List<String> userCardIds) {
        List<UserCardListRow> activeCards = userCardMapper.findActiveByUserId(userId);
        Set<String> activeCardIds = new HashSet<>();
        for (UserCardListRow activeCard : activeCards) {
            activeCardIds.add(activeCard.getUserCardId());
        }

        Set<String> requestedCardIds = new HashSet<>(userCardIds);
        if (requestedCardIds.size() != userCardIds.size() || !requestedCardIds.equals(activeCardIds)) {
            throw new InvalidCardOrderException("보유한 활성 카드 전체를 중복 없이 포함해야 합니다.");
        }

        userCardMapper.updateDisplayOrders(userId, userCardIds);
        evictHomeCardsCache(userId);
        return getMyCards(userId, false);
    }

    @Transactional
    public void disconnectCard(String userId, String userCardId) {
        if (userCardMapper.findByUserCardId(userCardId, userId) == null) {
            throw new UserCardNotFoundException();
        }
        // user_cards를 참조하는 자식 테이블부터 순차적으로 삭제해야 FK 제약을 위반하지 않는다.
        userCardMapper.deleteBenefitCalculationOutcomesByUserCardId(userCardId);
        userCardMapper.deleteBenefitUsagesByUserCardId(userCardId);
        userCardMapper.deleteOptionSelectionsByUserCardId(userCardId);
        userCardMapper.deletePerformanceSnapshotsByUserCardId(userCardId);
        userCardMapper.deletePaymentApprovalsByUserCardId(userCardId);
        if (userCardMapper.deleteUserCard(userCardId, userId) == 0) {
            throw new UserCardNotFoundException();
        }
        evictHomeCardsCache(userId);
    }

    /** 회원 탈퇴 시 user_cards와 그 자식 테이블을 모두 정리한다. */
    @Transactional
    public void deleteAllByUserId(String userId) {
        userCardMapper.deleteBenefitCalculationOutcomesByUserId(userId);
        userCardMapper.deleteBenefitUsagesByUserId(userId);
        userCardMapper.deleteOptionSelectionsByUserId(userId);
        userCardMapper.deletePerformanceSnapshotsByUserId(userId);
        userCardMapper.deletePaymentApprovalsByUserId(userId);
        userCardMapper.deleteUserCardsByUserId(userId);
    }

    @Transactional(readOnly = true)
    public CardDetailResponse getCardDetail(String userId, String userCardId) {
        UserCardListRow cardRow = userCardMapper.findByUserCardId(userCardId, userId);
        if (cardRow == null) {
            throw new UserCardNotFoundException();
        }

        List<CardBenefitRow> benefitRows = cardRow.getContentVersionId() == null
                ? Collections.emptyList()
                : cardBenefitMapper.findByContentVersionId(cardRow.getContentVersionId());

        List<CardBenefitResponse> benefits = benefitRows.stream()
                .filter(row -> "benefit".equals(row.getRecordType()))
                .map(CardBenefitResponse::new)
                .toList();
        List<CardBenefitResponse> notices = benefitRows.stream()
                .filter(row -> "notice".equals(row.getRecordType()))
                .map(CardBenefitResponse::new)
                .toList();

        return new CardDetailResponse(cardRow, benefits, notices);
    }

    /** 카드 목록·순서·별칭이 바뀌면 모든 월의 홈 카드 캐시가 즉시 최신값을 반영해야 한다. */
    private void evictHomeCardsCache(String userId) {
        if (homeCardsCache != null) {
            homeCardsCache.evictAll(userId);
        }
    }

    private List<MeCardItemResponse> mapCards(List<UserCardListRow> cardRows) {
        return cardRows.stream()
                .map(MeCardItemResponse::new)
                .toList();
    }
}
