package com.moca.mocabe.domain.card.exception;

/** 순서 변경 요청에 포함된 카드 목록이 사용자의 활성 카드 전체와 일치하지 않을 때 발생한다. */
public class InvalidCardOrderException extends RuntimeException {

    public InvalidCardOrderException(String message) {
        super(message);
    }
}
