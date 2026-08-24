package com.moca.mocabe.global.exception.benefit;

/** 혜택 재계산 요청의 기간이 유효하지 않을 때 발생한다. */
public class InvalidBenefitRecalculationException extends RuntimeException {

  public InvalidBenefitRecalculationException(String message) {
    super(message);
  }
}
