package com.moca.mocabe.domain.codef.model;

/** POST 연동 시 user_cards에 is_active=false로 적재할 보유카드 한 건이다. */
public record LinkedCardInsert(
        String userCardId,
        String linkId,
        String userId,
        String issuerId,
        String cardId,
        String cardNameFromCodef,
        String cardNo,
        String codefCardKeyHash,
        int displayOrder
) {
}
