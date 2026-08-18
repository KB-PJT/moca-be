package com.moca.mocabe.domain.benefit.structuring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("전월 실적 공통 파서")
class PerformanceParserTest {
  private final PerformanceParser parser = new PerformanceParser();

  @Test
  @DisplayName("실적 구간은 상한 미만을 함께 보존한다")
  void parsesExclusiveTierRange() {
    ParsedPerformanceTier tier =
        parser.parse("전월 이용금액 30만원 이상 70만원 미만 시 5% 할인", null, null).orElseThrow();

    assertEquals(0, tier.minimumKrw().compareTo(new BigDecimal("300000")));
    assertEquals(0, tier.maximumExclusiveKrw().compareTo(new BigDecimal("700000")));
  }

  @Test
  @DisplayName("상위 실적 구간은 하한만 보존한다")
  void parsesOpenEndedTier() {
    ParsedPerformanceTier tier = parser.parse(null, "지난달 이용실적 70만원 이상", null).orElseThrow();

    assertEquals(0, tier.minimumKrw().compareTo(new BigDecimal("700000")));
    assertEquals(null, tier.maximumExclusiveKrw());
  }

  @Test
  @DisplayName("천원 단위 실적과 실적 없음도 구분한다")
  void parsesThousandUnitAndMissingTier() {
    ParsedPerformanceTier tier = parser.parse("전월 실적 300천원 이상", null, null).orElseThrow();

    assertEquals(0, tier.minimumKrw().compareTo(new BigDecimal("300000")));
    assertEquals(true, parser.parse("실적 조건 없음", null, null).isEmpty());
  }
}
