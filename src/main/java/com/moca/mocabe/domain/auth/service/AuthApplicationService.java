package com.moca.mocabe.domain.auth.service;

import com.moca.mocabe.domain.auth.dto.GoogleLoginResponse;
import com.moca.mocabe.domain.auth.dto.RefreshTokenResponse;
import com.moca.mocabe.domain.user.dto.UserProfileResponse;
import com.moca.mocabe.domain.user.service.UserDomainService;
import com.moca.mocabe.domain.user.model.UserProfile;
import com.moca.mocabe.global.auth.GoogleIdTokenClaims;
import com.moca.mocabe.global.auth.GoogleIdTokenVerifier;
import com.moca.mocabe.global.auth.OpaqueTokenPair;
import com.moca.mocabe.global.auth.OpaqueTokenService;
import org.springframework.transaction.annotation.Transactional;

/** Google 신원 검증 후 MOCA opaque 세션을 발급하는 인증 유스케이스다. */
public class AuthApplicationService {

    private static final String DEFAULT_NICKNAME = "MOCA 회원";

    private final UserDomainService userDomainService;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final OpaqueTokenService opaqueTokenService;

    public AuthApplicationService(UserDomainService userDomainService,
                                  GoogleIdTokenVerifier googleIdTokenVerifier,
                                  OpaqueTokenService opaqueTokenService) {
        this.userDomainService = userDomainService;
        this.googleIdTokenVerifier = googleIdTokenVerifier;
        this.opaqueTokenService = opaqueTokenService;
    }

    @Transactional
    public GoogleLoginResponse login(String idToken) {
        GoogleIdTokenClaims claims = googleIdTokenVerifier.verify(idToken);
        UserDomainService.GoogleUserResult userResult = userDomainService.findOrCreateGoogleUser(
                claims.getSubject(), claims.getEmail(), nickname(claims.getName()));
        UserProfile userProfile = userResult.getUserProfile();

        OpaqueTokenPair tokens = opaqueTokenService.issue(userProfile.getUserId(), userProfile.getUserType());
        return new GoogleLoginResponse(userResult.isNewMember(), tokens, new UserProfileResponse(userProfile));
    }

    public RefreshTokenResponse refresh(String refreshToken) {
        return new RefreshTokenResponse(opaqueTokenService.refresh(refreshToken));
    }

    public void logout(String accessToken, String refreshToken) {
        opaqueTokenService.revoke(accessToken, refreshToken);
    }

    private String nickname(String name) {
        if (name == null || name.trim().isEmpty()) {
            return DEFAULT_NICKNAME;
        }
        return name.trim().length() > 50 ? name.trim().substring(0, 50) : name.trim();
    }

}
