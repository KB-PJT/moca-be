package com.moca.mocabe.domain.benefit.structuring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("거래 조건 공통 파서")
class TransactionConditionParserTest {
  private final TransactionConditionParser parser = new TransactionConditionParser();

  @Test
  @DisplayName("거래 적격 상한과 산식 인정 상한을 분리한다")
  void separatesEligibilityMaximumFromBenefitBaseMaximum() {
    ParsedTransactionCondition condition =
        parser.parse("건당 5만원 이하 결제 시, 결제금액 3만원까지 10% 할인", null, null).orElseThrow();

    assertEquals(0, condition.maximumEligiblePaymentKrw().compareTo(new BigDecimal("50000")));
    assertEquals(0, condition.maximumBenefitBaseKrw().compareTo(new BigDecimal("30000")));
  }

  @Test
  @DisplayName("천원 단위 최소 금액과 조건 없음도 구분한다")
  void parsesThousandMinimumAndMissingCondition() {
    ParsedTransactionCondition condition =
        parser.parse("건당 3천원 이상 결제 시 할인", null, null).orElseThrow();

    assertEquals(0, condition.minimumPaymentKrw().compareTo(new BigDecimal("3000")));
    assertEquals(true, parser.parse("조건 없는 할인", null, null).isEmpty());
  }
}
