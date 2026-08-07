package com.moca.mocabe.domain.codef.dto;

import java.util.List;

/**
 * 연동(카드사 계정) 한 건의 보유카드 재조회 결과다. success=false는 CODEF 조회 자체가 실패해
 * 다시 시도가 필요한 상태이며, cards가 빈 배열인 것과는 다르다(빈 배열은 정상 조회했지만
 * 매칭된 보유카드가 없다는 뜻이다).
 */
public record SyncOwnedCardsResult(
        String linkId,
        String institutionCode,
        boolean success,
        List<CardLinkCardResponse> cards
) {
}
