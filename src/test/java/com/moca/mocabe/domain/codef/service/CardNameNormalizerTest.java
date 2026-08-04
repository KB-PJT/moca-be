package com.moca.mocabe.domain.codef.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CardNameNormalizerTest {

    private final CardNameNormalizer normalizer = new CardNameNormalizer();

    @Test
    @DisplayName("대소문자·공백·특수문자를 무시하고 같은 이름으로 정규화한다")
    void normalizesCaseSpaceAndSymbols() {
        assertEquals(normalizer.normalize("노리2 체크카드(KB Pay)"),
                normalizer.normalize("노리2체크카드 (kb pay)"));
    }

    @Test
    @DisplayName("null 카드명은 빈 문자열로 정규화한다")
    void normalizesNullToEmpty() {
        assertEquals("", normalizer.normalize(null));
    }

    @Test
    @DisplayName("원본명과 '_' 앞 base명을 순서대로 후보로 만든다")
    void buildsOriginalThenUnderscoreBase() {
        assertEquals(List.of("노리2 체크카드(KB Pay)_비교통", "노리2 체크카드(KB Pay)"),
                normalizer.baseCandidates("노리2 체크카드(KB Pay)_비교통"));
    }

    @Test
    @DisplayName("'_'가 없으면 원본명만 후보로 둔다")
    void keepsOnlyOriginalWhenNoUnderscore() {
        assertEquals(List.of("노리2 체크카드"), normalizer.baseCandidates("노리2 체크카드"));
    }
}
