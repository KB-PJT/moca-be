package com.moca.mocabe.domain.user.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

/** 홈 보유 카드의 정렬 방식을 변경하는 요청이다. */
public class UpdateCardSortModeRequest {

    @NotBlank(message = "카드 정렬 방식은 필수입니다.")
    @Pattern(regexp = "AUTO|MANUAL", message = "카드 정렬 방식은 AUTO 또는 MANUAL이어야 합니다.")
    private String cardSortMode;

    public String getCardSortMode() {
        return cardSortMode;
    }

    public void setCardSortMode(String cardSortMode) {
        this.cardSortMode = cardSortMode;
    }
}
