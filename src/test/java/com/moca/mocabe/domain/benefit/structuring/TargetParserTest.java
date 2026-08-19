package com.moca.mocabe.domain.benefit.structuring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("혜택 대상 공통 파서")
class TargetParserTest {
  private final TargetParser parser = new TargetParser();

  @Test
  @DisplayName("일반 편의점 혜택은 category target으로 만든다")
  void parsesCategoryTarget() {
    ParsedTarget target = parser.parse("모든 편의점에서 10% 할인", null, null).orElseThrow();
    assertEquals(ParsedTarget.Type.MERCHANT_CATEGORY, target.type());
    assertEquals("CONVENIENCE_STORE", target.code());
  }

  @Test
  @DisplayName("국내 전 가맹점은 all merchants target으로 만든다")
  void parsesAllMerchantsTarget() {
    ParsedTarget target = parser.parse("국내 전 가맹점 0.2% 적립", null, null).orElseThrow();
    assertEquals(ParsedTarget.Type.ALL_MERCHANTS, target.type());
  }

  @Test
  @DisplayName("명시 가맹점 혜택은 넓은 업종 대신 개별 merchant target으로 만든다")
  void parsesExplicitMerchantWithoutBroadeningToCategory() {
    ParsedTarget target = parser.parse("GS25 편의점에서 10% 할인", null, null).orElseThrow();
    assertEquals(ParsedTarget.Type.MERCHANT, target.type());
    assertEquals("GS25", target.code());
  }

  @Test
  @DisplayName("merchant master에 있는 추가 브랜드도 정확한 merchant target으로 정규화한다")
  void parsesAdditionalExplicitMerchants() {
    assertMerchant("올리브영 결제 시 적립", "올리브영");
    assertMerchant("파리바게뜨 10% 할인", "파리바게뜨");
    assertMerchant("메가MGC커피 할인", "메가MGC커피");
    assertMerchant("롯데월드 이용권 할인", "롯데월드");
  }

  @Test
  @DisplayName("taxonomy에 있는 세부 업종 표현을 canonical category로 정규화한다")
  void parsesAdditionalCanonicalCategories() {
    assertCategory("전국 서점 5% 할인", "BOOKS");
    assertCategory("주유소 결제 시 적립", "FUEL");
    assertCategory("지하철 이용 할인", "SUBWAY");
    assertCategory("렌터카 10% 할인", "RENTAL_CAR");
    assertCategory("공연 예매 적립", "PERFORMANCE_EXHIBITION");
  }

  private void assertCategory(String text, String code) {
    ParsedTarget target = parser.parse(text, null, null).orElseThrow();
    assertEquals(ParsedTarget.Type.MERCHANT_CATEGORY, target.type());
    assertEquals(code, target.code());
  }

  private void assertMerchant(String text, String name) {
    ParsedTarget target = parser.parse(text, null, null).orElseThrow();
    assertEquals(ParsedTarget.Type.MERCHANT, target.type());
    assertEquals(name, target.code());
  }
}
