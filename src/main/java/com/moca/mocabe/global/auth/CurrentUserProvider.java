package com.moca.mocabe.global.auth;

/** 현재 요청의 인증 사용자 식별자를 제공한다. */
public interface CurrentUserProvider {

    String getCurrentUserId();
}
