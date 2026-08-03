package com.moca.mocabe.domain.codef.exception;

/** 후보·옵션 선택이 연동 조회 결과와 일치하지 않을 때 발생한다. */
public class InvalidCardSelectionException extends RuntimeException {

    public InvalidCardSelectionException(String message) {
        super(message);
    }
}
