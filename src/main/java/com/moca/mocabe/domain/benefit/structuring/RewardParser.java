package com.moca.mocabe.domain.benefit.structuring;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 원문에 명시된 단순 정률·정액 보상을 추출한다. 사용량 단위와 '최대' 단독 문구는 확정하지 않는다. */
public class RewardParser {
  private static final Pattern PERCENT = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)%\\s*(할인|청구할인|즉시할인|적립|캐시백)");
  private static final Pattern AMOUNT = Pattern.compile(
      "([0-9,]+|[0-9]+(?:\\.[0-9]+)?[천만])\\s*(원|P|포인트|마일)\\s*(할인|캐시백|적립)?");

  private final BenefitTextNormalizer normalizer = new BenefitTextNormalizer();

  public Optional<ParsedReward> parse(String detailText, String summary, String title) {
    String text = normalizer.normalize(detailText, summary, title);
    Matcher percent = PERCENT.matcher(text);
    if (percent.find() && !text.substring(0, percent.start()).matches(".*최대\\s*$")) {
      String suffix = percent.group(2);
      ParsedReward.Type type = "캐시백".equals(suffix) ? ParsedReward.Type.CASHBACK : ParsedReward.Type.PERCENT;
      return Optional.of(new ParsedReward(type, new BigDecimal(percent.group(1)), percent.group()));
    }
    Matcher amount = AMOUNT.matcher(text);
    if (!amount.find() || text.substring(0, amount.start()).matches(".*최대\\s*$")) {
      return Optional.empty();
    }
    BigDecimal value = amount(amount.group(1));
    String unit = amount.group(2);
    ParsedReward.Type type = switch (unit) {
      case "P", "포인트" -> ParsedReward.Type.POINT;
      case "마일" -> ParsedReward.Type.MILEAGE;
      default -> "캐시백".equals(amount.group(3))
          ? ParsedReward.Type.CASHBACK : ParsedReward.Type.FIXED_KRW;
    };
    return Optional.of(new ParsedReward(type, value, amount.group()));
  }

  private BigDecimal amount(String source) {
    String normalized = source.replace(",", "");
    if (normalized.endsWith("천")) {
      return new BigDecimal(normalized.substring(0, normalized.length() - 1)).multiply(BigDecimal.valueOf(1000));
    }
    if (normalized.endsWith("만")) {
      return new BigDecimal(normalized.substring(0, normalized.length() - 1)).multiply(BigDecimal.valueOf(10000));
    }
    return new BigDecimal(normalized);
  }
}
