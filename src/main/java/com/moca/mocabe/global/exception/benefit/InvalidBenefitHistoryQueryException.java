package com.moca.mocabe.global.exception.benefit;

/** 혜택 이력 조회 조건이 유효하지 않을 때 발생한다. */
public class InvalidBenefitHistoryQueryException extends RuntimeException {

    public InvalidBenefitHistoryQueryException(String message) {
        super(message);
    }
}
