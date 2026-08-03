package com.moca.mocabe.domain.codef.dto;

import java.util.List;

/** 활성화 처리 결과다. */
public record ActivateCardLinkCardsResponse(
        String linkId,
        List<String> activatedUserCardIds,
        int activatedCount
) {
}
