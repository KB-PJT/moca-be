package com.moca.mocabe.domain.codef.exception;

/** 현재 사용자가 선택 확정할 수 있는 카드 연동이 없을 때 발생한다. */
public class CardLinkNotFoundException extends RuntimeException {

    public CardLinkNotFoundException() {
        super("카드 연동을 찾을 수 없거나 이미 선택이 완료되었습니다.");
    }
}
