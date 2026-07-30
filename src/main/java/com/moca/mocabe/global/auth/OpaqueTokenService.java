package com.moca.mocabe.global.auth;

/** MOCA opaque token의 발급, 인증, 회전, 세션 폐기를 담당한다. */
public interface OpaqueTokenService {

    OpaqueTokenPair issue(String userId, String userType);

    OpaqueTokenPair refresh(String refreshToken);

    AuthenticatedUser authenticate(String accessToken);

    void revoke(String accessToken, String refreshToken);

    void revokeAll(String userId);
}
