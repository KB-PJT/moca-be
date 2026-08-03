package com.moca.mocabe.domain.codef.service;

import com.moca.mocabe.domain.codef.model.CardCatalogEntry;
import java.util.List;

/** 정규화 완전일치·base 완전일치 순서로 유일한 카드만 매칭한다. */
public class CardCatalogMatcher {

    private final CardNameNormalizer cardNameNormalizer;

    public CardCatalogMatcher(CardNameNormalizer cardNameNormalizer) {
        this.cardNameNormalizer = cardNameNormalizer;
    }

    /** catalog는 호출자가 발급사 기준으로 한 번만 조회해 넘긴다(카드 수만큼 반복 조회하지 않도록). */
    public CardCatalogEntry match(List<CardCatalogEntry> catalog, String codefCardName) {
        // 원본명 → '_' 뗀 base명 순서로 시도하고, 먼저 유일하게 일치하는 카드를 채택한다.
        List<String> candidates = cardNameNormalizer.baseCandidates(codefCardName);
        for (String candidate : candidates) {
            CardCatalogEntry match = uniqueNormalizedMatch(catalog, candidate);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private CardCatalogEntry uniqueNormalizedMatch(List<CardCatalogEntry> catalog, String candidate) {
        String normalized = cardNameNormalizer.normalize(candidate);
        // 정규화 후 정확히 1건일 때만 신뢰한다. 0건/2건 이상이면 오매칭 방지를 위해 미매칭 처리.
        List<CardCatalogEntry> matches = catalog.stream()
                .filter(card -> cardNameNormalizer.normalize(card.cardName()).equals(normalized))
                .toList();
        return matches.size() == 1 ? matches.get(0) : null;
    }
}
