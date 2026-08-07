package com.moca.mocabe.domain.card.service;

import com.moca.mocabe.domain.card.dto.MeCardItemResponse;
import com.moca.mocabe.domain.card.dto.MeCardsResponse;
import com.moca.mocabe.domain.card.mapper.UserCardMapper;
import com.moca.mocabe.domain.card.model.UserCardListRow;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/** 인증 사용자의 보유 카드 목록 조회 유스케이스를 담당한다. */
public class CardQueryService {

    private final UserCardMapper userCardMapper;

    public CardQueryService(UserCardMapper userCardMapper) {
        this.userCardMapper = userCardMapper;
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

    private List<MeCardItemResponse> mapCards(List<UserCardListRow> cardRows) {
        return cardRows.stream()
                .map(MeCardItemResponse::new)
                .toList();
    }
}
