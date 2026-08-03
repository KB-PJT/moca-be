 package com.moca.mocabe.domain.codef.dto;

import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;

/** 이미 적재된(비활성) 보유카드 중 활성화할 카드와, 활성 카드의 옵션 선택을 전달한다. */
public class ActivateCardLinkCardsRequest {

    @NotEmpty(message = "activeUserCardIds는 최소 한 건 이상이어야 합니다.")
    private List<String> activeUserCardIds;

    // 옵션 없는 카드만 활성화한다면 생략 가능(null 허용).
    @Valid
    private List<CardOptionSelectionRequest> optionSelections;

    public List<String> getActiveUserCardIds() {
        return activeUserCardIds;
    }

    public void setActiveUserCardIds(List<String> activeUserCardIds) {
        this.activeUserCardIds = activeUserCardIds;
    }

    public List<CardOptionSelectionRequest> getOptionSelections() {
        return optionSelections;
    }

    public void setOptionSelections(List<CardOptionSelectionRequest> optionSelections) {
        this.optionSelections = optionSelections;
    }
}
