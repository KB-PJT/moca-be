package com.moca.mocabe.domain.benefit.structuring;

import java.math.BigDecimal;

/** 거래 적격 최소·최대 금액과 혜택 계산 인정금액 상한을 구분한다. */
public record ParsedTransactionCondition(
    BigDecimal minimumPaymentKrw,
    BigDecimal maximumEligiblePaymentKrw,
    BigDecimal maximumBenefitBaseKrw) { }
