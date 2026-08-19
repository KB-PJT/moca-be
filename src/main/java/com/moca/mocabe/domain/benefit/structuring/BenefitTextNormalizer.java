package com.moca.mocabe.domain.benefit.structuring;

import java.text.Normalizer;
import java.util.Locale;

/** 카드 원문 파서가 공통으로 사용하는 보수적 텍스트·금액 정규화기다. */
public class BenefitTextNormalizer {

  public String normalize(String detailText, String summary, String title) {
    return normalizePart(detailText) + "\n" + normalizePart(summary) + "\n" + normalizePart(title);
  }

  public String normalizePart(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    return Normalizer.normalize(value, Normalizer.Form.NFKC)
        .replaceAll("(?is)<[^>]+>", " ")
        .replace("&nbsp;", " ")
        .replaceAll("[•·ㆍ]", " ")
        .replaceAll("[\\t\\r\\n]+", " ")
        .replaceAll("\\s+", " ")
        .trim();
  }

  public String compact(String value) {
    return normalizePart(value)
        .toUpperCase(Locale.ROOT)
        .replaceAll("\\s+", "")
        .replaceAll("[()\\[\\],]", "");
  }
}
