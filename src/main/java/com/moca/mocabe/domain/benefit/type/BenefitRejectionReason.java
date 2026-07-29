package com.moca.mocabe.domain.benefit.type;

/**
 * 혜택이 적용되지 않는 대표 사유다.
 */
public enum BenefitRejectionReason {
    NONE,
    CATEGORY_NOT_MATCHED,
    MIN_PAYMENT_NOT_MET,
    PERFORMANCE_NOT_MET,
    CONDITION_NOT_MET,
    MERCHANT_NOT_ELIGIBLE,
    PAYMENT_CHANNEL_NOT_ELIGIBLE,
    FREQUENCY_LIMIT_EXHAUSTED,
    MONTHLY_LIMIT_EXHAUSTED
}
