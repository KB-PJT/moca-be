package com.moca.mocabe.domain.codef.service;

import com.moca.mocabe.domain.codef.mapper.CardCatalogMapper;
import com.moca.mocabe.domain.codef.model.CardCatalogEntry;
import java.util.List;

/** 정규화 완전일치·base 완전일치 순서로 유일한 카드만 매칭한다. */
public class CardCatalogMatcher {

    private final CardCatalogMapper cardCatalogMapper;
    private final CardNameNormalizer cardNameNormalizer;

    public CardCatalogMatcher(CardCatalogMapper cardCatalogMapper,
                              CardNameNormalizer cardNameNormalizer) {
        this.cardCatalogMapper = cardCatalogMapper;
        this.cardNameNormalizer = cardNameNormalizer;
    }

    public CardCatalogEntry match(String issuerId, String codefCardName) {
        List<CardCatalogEntry> cards = cardCatalogMapper.findCardsByIssuerId(issuerId);
        // 원본명 → '_' 뗀 base명 순서로 시도하고, 먼저 유일하게 일치하는 카드를 채택한다.
        List<String> candidates = cardNameNormalizer.baseCandidates(codefCardName);
        for (String candidate : candidates) {
            CardCatalogEntry match = uniqueNormalizedMatch(cards, candidate);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private CardCatalogEntry uniqueNormalizedMatch(List<CardCatalogEntry> cards, String candidate) {
        String normalized = cardNameNormalizer.normalize(candidate);
        // 정규화 후 정확히 1건일 때만 신뢰한다. 0건/2건 이상이면 오매칭 방지를 위해 미매칭 처리.
        List<CardCatalogEntry> matches = cards.stream()
                .filter(card -> cardNameNormalizer.normalize(card.cardName()).equals(normalized))
                .toList();
        return matches.size() == 1 ? matches.get(0) : null;
    }
}
