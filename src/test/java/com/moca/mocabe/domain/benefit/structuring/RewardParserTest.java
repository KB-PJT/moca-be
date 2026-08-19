package com.moca.mocabe.domain.benefit.structuring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("카드 혜택 공통 보상 파서")
class RewardParserTest {
  private final RewardParser parser = new RewardParser();

  @Test
  @DisplayName("정률 할인과 캐시백을 보상 유형으로 구분한다")
  void parsesPercentRewards() {
    ParsedReward discount = parser.parse("<p>전월 실적 30만원 이상 10% 청구할인</p>", null, null).orElseThrow();
    ParsedReward cashback = parser.parse(null, "5% 캐시백", null).orElseThrow();

    assertEquals(ParsedReward.Type.PERCENT, discount.type());
    assertEquals(0, discount.value().compareTo(new BigDecimal("10")));
    assertEquals(ParsedReward.Type.CASHBACK, cashback.type());
  }

  @Test
  @DisplayName("한글 단위와 포인트를 정액 보상으로 정규화한다")
  void parsesFixedRewards() {
    ParsedReward krw = parser.parse("1만 원 즉시할인", null, null).orElseThrow();
    ParsedReward point = parser.parse(null, "2,000P 적립", null).orElseThrow();

    assertEquals(ParsedReward.Type.FIXED_KRW, krw.type());
    assertEquals(0, krw.value().compareTo(new BigDecimal("10000")));
    assertEquals(ParsedReward.Type.POINT, point.type());
    assertEquals(0, point.value().compareTo(new BigDecimal("2000")));
  }

  @Test
  @DisplayName("세부 조건 없는 최대 문구는 확정 보상으로 만들지 않는다")
  void doesNotParseMaximumOnlyClaim() {
    assertFalse(parser.parse("최대 10% 할인", null, null).isPresent());
    assertTrue(parser.parse("결제금액의 10% 할인", null, null).isPresent());
  }

  @Test
  @DisplayName("정액 캐시백·마일리지와 천원 단위를 구분한다")
  void parsesEveryFixedRewardUnit() {
    ParsedReward cashback = parser.parse("3천원 캐시백", null, null).orElseThrow();
    ParsedReward mileage = parser.parse("500마일 적립", null, null).orElseThrow();

    assertEquals(ParsedReward.Type.CASHBACK, cashback.type());
    assertEquals(0, cashback.value().compareTo(new BigDecimal("3000")));
    assertEquals(ParsedReward.Type.MILEAGE, mileage.type());
  }
}
