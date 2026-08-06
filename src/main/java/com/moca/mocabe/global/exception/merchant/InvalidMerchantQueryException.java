package com.moca.mocabe.global.exception.merchant;

/** 가맹점 API의 조회 조건이 유효하지 않을 때 발생한다. */
public class InvalidMerchantQueryException extends RuntimeException {

    public InvalidMerchantQueryException(String message) {
        super(message);
    }
}
