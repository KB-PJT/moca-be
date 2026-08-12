package com.moca.mocabe.domain.codef.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** CODEF 카드명과 카드 마스터명을 완전 일치 비교하기 위한 정규화 규칙이다. */
public class CardNameNormalizer {

    // CODEF가 상품명 맨 앞/뒤에만 붙이는 신용/구버전 표기. 괄호·중괄호 짝이 맞고 문자열 시작 또는 끝에 있을 때만 제거해
    // "카드 (NEW) 에디션"처럼 상품명 내부에 있는 표기나 짝이 안 맞는 괄호는 건드리지 않는다.
    private static final String CODEF_TAG = "(?:\\(신\\)|\\[신\\]|\\(구\\)|\\[구\\]|\\(NEW\\)|\\[NEW\\])";
    private static final Pattern CODEF_TAG_PATTERN =
            Pattern.compile("^\\s*" + CODEF_TAG + "\\s*|\\s*" + CODEF_TAG + "\\s*$", Pattern.CASE_INSENSITIVE);

    /** 대소문자·공백·특수문자 차이를 없앤 비교용 문자열로 정규화한다. */
    public String normalize(String cardName) {
        if (cardName == null) {
            return "";
        }
        String withoutCodefTag = CODEF_TAG_PATTERN.matcher(cardName).replaceAll("");
        return Normalizer.normalize(withoutCodefTag, Normalizer.Form.NFKC)
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
