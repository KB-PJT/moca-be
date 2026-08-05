package com.moca.mocabe.domain.codef.model;

/** 특정 연동에 이미 적재된 보유카드의 카드 키 해시와 그 user_card_id다. 보유카드 재조회 시 중복 적재를 막는 데 쓰인다. */
public record LinkedCardKeyRow(
        String userCardId,
        String codefCardKeyHash
) {
}
