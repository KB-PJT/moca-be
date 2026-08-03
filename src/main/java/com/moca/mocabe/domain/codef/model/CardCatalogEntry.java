package com.moca.mocabe.domain.codef.model;

/** 카드고릴라 파서가 적재한 카드 마스터의 매칭용 조회 모델이다. */
public record CardCatalogEntry(
        String cardId,
        String issuerId,
        String cardName,
        String cardType,
        String imageUrl
) {
}
