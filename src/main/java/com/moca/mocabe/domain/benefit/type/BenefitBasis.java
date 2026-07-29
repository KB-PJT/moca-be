package com.moca.mocabe.domain.benefit.type;

/**
 * 혜택 금액을 계산하는 기준이다.
 */
public enum BenefitBasis {
    /**
     * 결제금액에 비율을 곱해 계산한다.
     */
    RATE,

    /**
     * 결제금액과 관계없이 정해진 값을 제공한다.
     */
    FIXED,

    /**
     * 일정 결제 단위마다 정해진 값을 제공한다.
     */
    PER_SPEND_UNIT,

    /**
     * 리터, 회, 건 등 결제금액이 아닌 사용량 단위마다 정해진 값을 제공한다.
     */
    PER_USAGE_UNIT
}
