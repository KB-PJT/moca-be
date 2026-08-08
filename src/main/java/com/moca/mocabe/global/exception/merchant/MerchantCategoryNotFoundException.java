package com.moca.mocabe.global.exception.merchant;

/** 요청한 categoryId가 지도 대상 카테고리로 존재하지 않을 때 발생한다. */
public class MerchantCategoryNotFoundException extends RuntimeException {

    public MerchantCategoryNotFoundException(String message) {
        super(message);
    }
}
