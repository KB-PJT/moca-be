package com.moca.mocabe.domain.codef.exception;

/** 같은 사용자가 동일한 카드사 계정이나 카드를 이미 연동했을 때 발생한다. */
public class CodefAccountAlreadyLinkedException extends RuntimeException {

    public CodefAccountAlreadyLinkedException() {
        super("이미 연동된 카드사 계정입니다.");
    }
}
