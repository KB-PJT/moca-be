package com.moca.mocabe.domain.benefit.structuring;

import java.math.BigDecimal;

/** 원문에서 보수적으로 추출한 한 건의 보상 산식이다. */
public record ParsedReward(Type type, BigDecimal value, String sourceText) {
  public enum Type {
    PERCENT,
    FIXED_KRW,
    POINT,
    MILEAGE,
    CASHBACK
  }
}
