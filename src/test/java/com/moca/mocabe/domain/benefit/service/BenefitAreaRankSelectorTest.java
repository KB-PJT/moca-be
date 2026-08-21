package com.moca.mocabe.domain.benefit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.moca.mocabe.domain.benefit.model.BenefitAreaSpendRow;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("월간 혜택 영역 최다 이용 판정")
class BenefitAreaRankSelectorTest {
  private final BenefitAreaRankSelector selector = new BenefitAreaRankSelector();

  @Test
  @DisplayName("사용금액이 가장 큰 영역을 선택한다")
  void selectsLargestArea() {
    BenefitAreaSpendRow selected = selector.selectTop(List.of(
        spend("RETAIL_STORE", 2, "20000"), spend("ENJOY_STORE", 3, "30000")));

    assertEquals("ENJOY_STORE", selected.areaKey());
  }

  @Test
  @DisplayName("사용금액이 같으면 표시 순위가 빠른 영역을 선택한다")
  void resolvesTieByDisplayOrder() {
    BenefitAreaSpendRow selected = selector.selectTop(List.of(
        spend("ENJOY_STORE", 3, "20000"), spend("DISCOUNT_STORE", 1, "20000")));

    assertEquals("DISCOUNT_STORE", selected.areaKey());
  }

  @Test
  @DisplayName("사용금액이 없으면 최다 영역을 선택하지 않는다")
  void returnsNullWhenNoSpendExists() {
    assertNull(selector.selectTop(List.of(spend("RETAIL_STORE", 2, "0"))));
    assertNull(selector.selectTop(null));
    assertNull(selector.selectTop(List.of()));
  }

  private BenefitAreaSpendRow spend(String key, int order, String amount) {
    return new BenefitAreaSpendRow("DREAM", key, key, order, new BigDecimal(amount), 1);
  }
}
