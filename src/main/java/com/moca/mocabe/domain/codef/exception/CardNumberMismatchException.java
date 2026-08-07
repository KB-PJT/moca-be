package com.moca.mocabe.domain.codef.exception;

/**
 * 카드별 카드번호/비밀번호 추가 입력 시, 입력한 카드번호가 이 카드의 저장된 마스킹 카드번호와
 * 일치하지 않을 때 발생한다. CODEF 호출 전에 걸러내 불필요한 상류 호출을 막는다.
 */
public class CardNumberMismatchException extends RuntimeException {

    public CardNumberMismatchException() {
        super("입력한 카드번호가 이 카드와 일치하지 않습니다.");
    }
}
