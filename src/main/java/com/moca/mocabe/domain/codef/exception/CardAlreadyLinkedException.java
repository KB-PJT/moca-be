package com.moca.mocabe.domain.codef.exception;

/** 같은 사용자가 동일한 보유카드(codef_card_key_hash)를 이미 적재했을 때 발생한다. */
public class CardAlreadyLinkedException extends RuntimeException {

    public CardAlreadyLinkedException(Throwable cause) {
        super("이미 등록된 보유카드입니다.", cause);
    }
}
