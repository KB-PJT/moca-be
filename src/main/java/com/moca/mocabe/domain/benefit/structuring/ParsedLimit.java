package com.moca.mocabe.domain.benefit.structuring;

import java.math.BigDecimal;

/** 원문 한도 한 건이다. amount와 count를 같은 값으로 취급하지 않는다. */
public record ParsedLimit(Period period, Type type, BigDecimal value) {
  public enum Period { DAILY, MONTHLY, YEARLY }
  public enum Type { AMOUNT, COUNT }
}
