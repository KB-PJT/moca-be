package com.moca.mocabe.domain.codef.model;

/**
 * POST 연동 시 user_cards에 적재할 보유카드 한 건이다. 항상 is_active=false로 적재된다. 카드번호가
 * 필요한 카드사에서 계정 생성 시 입력한 카드번호와 일치하는 카드는 cardNumberEnc/cardPasswordEnc를
 * 미리 채워두지만, 실제 활성화는 이 시점에 하지 않고 이후 PATCH /card-links/{linkId}/cards 요청으로
 * 별도로 이뤄진다.
 */
public record LinkedCardInsert(
        String userCardId,
        String linkId,
        String userId,
        String issuerId,
        String cardId,
        String cardNameFromCodef,
        String cardNo,
        String codefCardKeyHash,
        int displayOrder,
        byte[] cardNumberEnc,
        byte[] cardPasswordEnc,
        boolean isActive
) {
}
