package com.moca.mocabe.domain.card.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** 활성 카드와 선택적인 비활성 카드 목록을 반환한다. */
public class MeCardsResponse {

    private final String lastSyncedAt;
    private final List<MeCardItemResponse> activeCards;
    private final List<MeCardItemResponse> inactiveCards;

    public MeCardsResponse(
            String lastSyncedAt,
            List<MeCardItemResponse> activeCards,
            List<MeCardItemResponse> inactiveCards
    ) {
        this.lastSyncedAt = lastSyncedAt;
        this.activeCards = activeCards;
        this.inactiveCards = inactiveCards;
    }

    public String getLastSyncedAt() {
        return lastSyncedAt;
    }

    public List<MeCardItemResponse> getActiveCards() {
        return activeCards;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public List<MeCardItemResponse> getInactiveCards() {
        return inactiveCards;
    }
}
