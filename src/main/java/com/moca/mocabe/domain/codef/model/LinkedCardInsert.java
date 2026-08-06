package com.moca.mocabe.domain.codef.model;

/**
 * POST 연동 시 user_cards에 적재할 보유카드 한 건이다. 보통 is_active=false로 적재되지만, 카드번호가
 * 필요한 카드사에서 계정 생성 시 입력한 카드번호와 일치하는 카드는 cardNumberEnc/cardPasswordEnc를
 * 채워 isActive=true로 즉시 활성화 적재한다.
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
