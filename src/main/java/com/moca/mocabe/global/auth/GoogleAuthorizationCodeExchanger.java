package com.moca.mocabe.global.auth;

/** Google authorization code를 서버에서 교환하고 access token 정보를 검증한다. */
public interface GoogleAuthorizationCodeExchanger {

    GoogleUserIdentity exchangeAndVerify(String code, String codeVerifier);
}
