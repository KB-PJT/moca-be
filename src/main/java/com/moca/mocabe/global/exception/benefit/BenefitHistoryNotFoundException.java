package com.moca.mocabe.global.exception.benefit;

/** 로그인 사용자가 조회할 수 있는 혜택 이력이 없을 때 발생한다. */
public class BenefitHistoryNotFoundException extends RuntimeException {

    public BenefitHistoryNotFoundException() {
        super("혜택 내역을 찾을 수 없습니다.");
    }
}
