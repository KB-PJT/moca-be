package com.moca.mocabe.domain.codef.dto;

import java.util.List;

/** POST /card-links/cards/sync 응답으로, 연동(카드사 계정)별 보유카드 재조회 결과 목록이다. */
public record SyncOwnedCardsResponse(
        List<SyncOwnedCardsResult> results
) {
}
