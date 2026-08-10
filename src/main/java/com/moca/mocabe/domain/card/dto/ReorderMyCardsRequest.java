package com.moca.mocabe.domain.card.dto;

import java.util.List;
import javax.validation.constraints.NotEmpty;

/** 보유 카드 목록의 새 순서를 나타낸다. 활성 카드 전체를 원하는 순서대로 중복 없이 포함해야 한다. */
public class ReorderMyCardsRequest {

    @NotEmpty(message = "userCardIds는 최소 한 건 이상이어야 합니다.")
    private List<String> userCardIds;

    public List<String> getUserCardIds() {
        return userCardIds;
    }

    public void setUserCardIds(List<String> userCardIds) {
        this.userCardIds = userCardIds;
    }
}
