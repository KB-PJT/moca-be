package com.moca.mocabe.domain.benefit.structuring;

/** 원문에서 확정한 target 종류와 canonical code 후보다. */
public record ParsedTarget(Type type, String code) {
  public enum Type { MERCHANT, MERCHANT_CATEGORY, ALL_MERCHANTS }
}
