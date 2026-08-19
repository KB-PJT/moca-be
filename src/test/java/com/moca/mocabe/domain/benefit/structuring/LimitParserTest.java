package com.moca.mocabe.domain.benefit.structuring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("혜택 한도 공통 파서")
class LimitParserTest {
  private final LimitParser parser = new LimitParser();

  @Test
  @DisplayName("금액 한도와 횟수 한도를 분리한다")
  void parsesAmountAndCountLimits() {
    List<ParsedLimit> limits = parser.parse("월 할인한도 1만원, 일 최대 3천원, 월 2회, 연 12회", null, null);

    assertEquals(4, limits.size());
    assertEquals(new ParsedLimit(ParsedLimit.Period.MONTHLY, ParsedLimit.Type.AMOUNT,
        new BigDecimal("10000")), limits.get(0));
    assertEquals(new ParsedLimit(ParsedLimit.Period.YEARLY, ParsedLimit.Type.COUNT,
        new BigDecimal("12")), limits.get(3));
  }
}
