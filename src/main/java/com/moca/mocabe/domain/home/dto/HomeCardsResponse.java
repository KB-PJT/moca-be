package com.moca.mocabe.domain.home.dto;

import java.util.List;

/** 홈 카드 캐러셀과 선택 카드 요약이다. */
public class HomeCardsResponse {

    private final String yearMonth;
    private final String orderMode;
    private final String selectedUserCardId;
    private final List<HomeCardResponse> cards;

    public HomeCardsResponse(String yearMonth, String orderMode, String selectedUserCardId,
                             List<HomeCardResponse> cards) {
        this.yearMonth = yearMonth;
        this.orderMode = orderMode;
        this.selectedUserCardId = selectedUserCardId;
        this.cards = cards;
    }

    public String getYearMonth() {
        return yearMonth;
    }

    public String getOrderMode() {
        return orderMode;
    }

    public String getSelectedUserCardId() {
        return selectedUserCardId;
    }

    public List<HomeCardResponse> getCards() {
        return cards;
    }
}
