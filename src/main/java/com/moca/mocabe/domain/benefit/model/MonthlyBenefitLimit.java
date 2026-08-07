package com.moca.mocabe.domain.benefit.model;

import java.math.BigDecimal;

/** 현재 전월 실적 구간에서 적용되는 월 보상 한도다. */
public record MonthlyBenefitLimit(
    String limitPolicyId, String sharedGroupKey, BigDecimal limitValue) { }
