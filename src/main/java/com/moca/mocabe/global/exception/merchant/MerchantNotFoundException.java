package com.moca.mocabe.global.exception.merchant;

/** 요청한 merchantId가 해당 카테고리에 존재하지 않을 때 발생한다. */
public class MerchantNotFoundException extends RuntimeException {

    public MerchantNotFoundException(String message) {
        super(message);
    }
}
