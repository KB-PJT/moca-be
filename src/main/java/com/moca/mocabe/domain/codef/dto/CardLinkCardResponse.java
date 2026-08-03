package com.moca.mocabe.domain.codef.dto;

import java.util.List;

/** 연동으로 조회된 보유카드 한 건. 매칭 성공 카드는 is_active=false로 적재되어 userCardId가 있고, 미매칭은 null이다. */
public record CardLinkCardResponse(
        String userCardId,
        String cardId,
        String cardName,
        String cardNo,
        String institutionCode,
        String issuerName,
        String cardType,
        String cardImageUrl,
        boolean matched,
        boolean supported,
        List<CardOptionGroupResponse> optionGroups
) {
}
