package com.moca.mocabe.domain.benefit.structuring;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 일·월·연 단위 금액/횟수 한도를 각각 추출한다. */
public class LimitParser {
  private static final Pattern AMOUNT = Pattern.compile("(일|월|연)\\s*(?:최대|할인한도)?\\s*([0-9,]+[천만])원");
  private static final Pattern COUNT = Pattern.compile("(일|월|연)\\s*(?:최대)?\\s*([0-9]+)회");
  private final BenefitTextNormalizer normalizer = new BenefitTextNormalizer();

  public List<ParsedLimit> parse(String detailText, String summary, String title) {
    String text = normalizer.normalize(detailText, summary, title);
    List<ParsedLimit> limits = new ArrayList<>();
    add(limits, AMOUNT.matcher(text), ParsedLimit.Type.AMOUNT);
    add(limits, COUNT.matcher(text), ParsedLimit.Type.COUNT);
    return List.copyOf(limits);
  }

  private void add(List<ParsedLimit> limits, Matcher matcher, ParsedLimit.Type type) {
    while (matcher.find()) {
      BigDecimal value = type == ParsedLimit.Type.COUNT
          ? new BigDecimal(matcher.group(2)) : amount(matcher.group(2));
      limits.add(new ParsedLimit(period(matcher.group(1)), type, value));
    }
  }

  private ParsedLimit.Period period(String value) {
    return switch (value) {
      case "일" -> ParsedLimit.Period.DAILY;
      case "월" -> ParsedLimit.Period.MONTHLY;
      default -> ParsedLimit.Period.YEARLY;
    };
  }

  private BigDecimal amount(String source) {
    String value = source.replace(",", "");
    BigDecimal multiplier = value.endsWith("천") ? BigDecimal.valueOf(1000) : BigDecimal.valueOf(10000);
    return new BigDecimal(value.substring(0, value.length() - 1)).multiply(multiplier);
  }
}
