package com.moca.mocabe.domain.benefit.structuring;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 거래 적격 상한과 산식 인정 상한을 서로 다른 필드로 보존한다. */
public class TransactionConditionParser {
  private static final Pattern MINIMUM = Pattern.compile("(?:건당|1회|결제금액)\\s*([0-9,]+[천만])원?\\s*이상");
  private static final Pattern ELIGIBLE_MAX = Pattern.compile("(?:건당|1회|결제금액)\\s*([0-9,]+[천만])원?\\s*이하");
  private static final Pattern BENEFIT_BASE_MAX = Pattern.compile(
      "(?:이용금액|결제금액).{0,12}?([0-9,]+[천만])원?\\s*까지.*?(?:할인|적립|캐시백)");

  private final BenefitTextNormalizer normalizer = new BenefitTextNormalizer();

  public Optional<ParsedTransactionCondition> parse(String detailText, String summary, String title) {
    String text = normalizer.normalize(detailText, summary, title);
    BigDecimal minimum = find(MINIMUM, text);
    BigDecimal eligibleMaximum = find(ELIGIBLE_MAX, text);
    BigDecimal benefitBaseMaximum = find(BENEFIT_BASE_MAX, text);
    return minimum == null && eligibleMaximum == null && benefitBaseMaximum == null
        ? Optional.empty()
        : Optional.of(new ParsedTransactionCondition(minimum, eligibleMaximum, benefitBaseMaximum));
  }

  private BigDecimal find(Pattern pattern, String text) {
    Matcher matcher = pattern.matcher(text);
    return matcher.find() ? amount(matcher.group(1)) : null;
  }

  private BigDecimal amount(String source) {
    String value = source.replace(",", "");
    BigDecimal multiplier = value.endsWith("천") ? BigDecimal.valueOf(1000) : BigDecimal.valueOf(10000);
    return new BigDecimal(value.substring(0, value.length() - 1)).multiply(multiplier);
  }
}
