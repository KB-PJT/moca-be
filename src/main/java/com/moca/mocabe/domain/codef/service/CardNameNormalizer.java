package com.moca.mocabe.domain.codef.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** CODEF 카드명과 카드 마스터명을 완전 일치 비교하기 위한 정규화 규칙이다. */
public class CardNameNormalizer {

    /** 대소문자·공백·특수문자 차이를 없앤 비교용 문자열로 정규화한다. */
    public String normalize(String cardName) {
        if (cardName == null) {
            return "";
        }
        return Normalizer.normalize(cardName, Normalizer.Form.NFKC)
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    /** 원본명과, '_' 뒤 CODEF 내부 표기를 떼어낸 base명을 매칭 시도 순서대로 돌려준다. */
    public List<String> baseCandidates(String cardName) {
        String source = cardName == null ? "" : cardName;
        List<String> candidates = new ArrayList<>();
        candidates.add(source);
        // 예: "노리2 체크카드(KB Pay)_비교통" → 원본으로 못 잡으면 "_비교통"을 뗀 base명으로 재시도.
        int underscore = source.indexOf('_');
        if (underscore > 0) {
            candidates.add(source.substring(0, underscore));
        }
        return candidates;
    }
}
