package com.moca.mocabe.global.auth;

/** Google JWKS 기반 ID Token 검증을 추상화한다. */
public interface GoogleIdTokenVerifier {

    GoogleIdTokenClaims verify(String idToken);
}
