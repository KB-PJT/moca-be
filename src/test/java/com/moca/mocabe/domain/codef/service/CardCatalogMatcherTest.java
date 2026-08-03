package com.moca.mocabe.domain.codef.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.moca.mocabe.domain.codef.model.CardCatalogEntry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CardCatalogMatcherTest {

    private static final String ISSUER_ID = "00000000-0000-4000-8000-000000000301";
    private CardCatalogMatcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new CardCatalogMatcher(new CardNameNormalizer());
    }

    @Test
    @DisplayName("공백·특수문자·대소문자를 정규화해 유일 완전일치한다")
    void matchesUniqueNormalizedName() {
        CardCatalogEntry expected = card("card-1", "노리2 체크카드(KB Pay)");

        assertEquals(expected, matcher.match(List.of(expected), "노리2체크카드 (kb pay)"));
    }

    @Test
    @DisplayName("밑줄 뒤 CODEF 내부 표기는 base 이름으로 다시 매칭한다")
    void matchesBaseNames() {
        CardCatalogEntry expected = card("card-1", "노리2 체크카드(KB Pay)");

        assertEquals(expected, matcher.match(List.of(expected), "노리2 체크카드(KB Pay)_비교통"));
    }

    @Test
    @DisplayName("정규화 결과가 둘 이상이면 오매칭하지 않고 미매칭 처리한다")
    void rejectsAmbiguousMatch() {
        List<CardCatalogEntry> catalog = List.of(
                card("card-1", "톡톡 카드"), card("card-2", "톡톡카드"));

        assertNull(matcher.match(catalog, "톡톡카드"));
    }

    @Test
    @DisplayName("일치 카드가 없거나 입력이 null이면 미매칭 처리한다")
    void returnsNullWhenNoMatch() {
        assertNull(matcher.match(List.of(card("card-1", "다른 카드")), null));
    }

    private CardCatalogEntry card(String id, String name) {
        return new CardCatalogEntry(id, ISSUER_ID, name, "CHECK", "https://cards/" + id + ".png");
    }
}
