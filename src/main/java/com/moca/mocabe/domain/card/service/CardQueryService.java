package com.moca.mocabe.domain.card.service;

import com.moca.mocabe.domain.card.dto.CardBenefitResponse;
import com.moca.mocabe.domain.card.dto.CardDetailResponse;
import com.moca.mocabe.domain.card.dto.MeCardItemResponse;
import com.moca.mocabe.domain.card.dto.MeCardsResponse;
import com.moca.mocabe.domain.card.mapper.CardBenefitMapper;
import com.moca.mocabe.domain.card.mapper.UserCardMapper;
import com.moca.mocabe.domain.card.model.CardBenefitRow;
import com.moca.mocabe.domain.card.model.UserCardListRow;
import com.moca.mocabe.domain.codef.exception.UserCardNotFoundException;
import java.util.Collections;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/** 인증 사용자의 보유 카드 목록·상세 조회 유스케이스를 담당한다. */
public class CardQueryService {

    private final UserCardMapper userCardMapper;
    private final CardBenefitMapper cardBenefitMapper;

    public CardQueryService(UserCardMapper userCardMapper, CardBenefitMapper cardBenefitMapper) {
        this.userCardMapper = userCardMapper;
        this.cardBenefitMapper = cardBenefitMapper;
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
        return new MeCardItemResponse(userCardMapper.findByUserCardId(userCardId, userId));
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

    private List<MeCardItemResponse> mapCards(List<UserCardListRow> cardRows) {
        return cardRows.stream()
                .map(MeCardItemResponse::new)
                .toList();
    }
}
