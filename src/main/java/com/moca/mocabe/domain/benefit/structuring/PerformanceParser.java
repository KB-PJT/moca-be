package com.moca.mocabe.domain.benefit.structuring;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 전월 실적의 단일 하한 또는 명시적인 반개구간 범위를 파싱한다. */
public class PerformanceParser {
  private static final String PREFIX = "(?:전월\\s*(?:실적|이용금액|이용실적)|지난달\\s*이용실적)";
  private static final Pattern RANGE = Pattern.compile(
      PREFIX + "?\\s*([0-9,]+(?:\\.[0-9]+)?[천만])원?\\s*이상\\s*([0-9,]+(?:\\.[0-9]+)?[천만])원?\\s*미만");
  private static final Pattern MINIMUM = Pattern.compile(
      PREFIX + "?\\s*([0-9,]+(?:\\.[0-9]+)?[천만])원?\\s*이상");

  private final BenefitTextNormalizer normalizer = new BenefitTextNormalizer();

  public Optional<ParsedPerformanceTier> parse(String detailText, String summary, String title) {
    String text = normalizer.normalize(detailText, summary, title);
    Matcher range = RANGE.matcher(text);
    if (range.find()) {
      return Optional.of(new ParsedPerformanceTier(amount(range.group(1)), amount(range.group(2))));
    }
    Matcher minimum = MINIMUM.matcher(text);
    if (minimum.find()) {
      return Optional.of(new ParsedPerformanceTier(amount(minimum.group(1)), null));
    }
    return Optional.empty();
  }

  private BigDecimal amount(String source) {
    String value = source.replace(",", "");
    BigDecimal multiplier = value.endsWith("천") ? BigDecimal.valueOf(1000) : BigDecimal.valueOf(10000);
    return new BigDecimal(value.substring(0, value.length() - 1)).multiply(multiplier);
  }
}
