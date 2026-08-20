package com.moca.mocabe.domain.auth.service;

import com.moca.mocabe.domain.auth.dto.GoogleLoginResponse;
import com.moca.mocabe.domain.auth.dto.RefreshTokenResponse;
import com.moca.mocabe.domain.notification.service.DeviceService;
import com.moca.mocabe.domain.user.dto.UserProfileResponse;
import com.moca.mocabe.domain.user.service.UserDomainService;
import com.moca.mocabe.domain.user.model.UserProfile;
import com.moca.mocabe.global.auth.AuthenticatedUser;
import com.moca.mocabe.global.auth.GoogleAuthorizationCodeExchanger;
import com.moca.mocabe.global.auth.GoogleUserIdentity;
import com.moca.mocabe.global.auth.OpaqueTokenPair;
import com.moca.mocabe.global.auth.OpaqueTokenService;
import com.moca.mocabe.global.exception.auth.InvalidOpaqueTokenException;
import org.springframework.transaction.annotation.Transactional;

/** Google authorization code 검증 후 MOCA opaque 세션을 발급하는 인증 유스케이스다. */
public class AuthApplicationService {

    private final UserDomainService userDomainService;
    private final GoogleAuthorizationCodeExchanger googleAuthorizationCodeExchanger;
    private final OpaqueTokenService opaqueTokenService;
    private final DeviceService deviceService;

    public AuthApplicationService(UserDomainService userDomainService,
                                  GoogleAuthorizationCodeExchanger googleAuthorizationCodeExchanger,
                                  OpaqueTokenService opaqueTokenService,
                                  DeviceService deviceService) {
        this.userDomainService = userDomainService;
        this.googleAuthorizationCodeExchanger = googleAuthorizationCodeExchanger;
        this.opaqueTokenService = opaqueTokenService;
        this.deviceService = deviceService;
    }

    @Transactional
    public GoogleLoginResponse login(String code, String codeVerifier, String redirectUri) {
        GoogleUserIdentity identity = googleAuthorizationCodeExchanger.exchangeAndVerify(code, codeVerifier,
                redirectUri);
        UserDomainService.GoogleUserResult userResult = userDomainService.findOrCreateGoogleUser(
                identity.getSubject(), identity.getEmail(), identity.getProfileName());
        UserProfile userProfile = userResult.getUserProfile();

        OpaqueTokenPair tokens = opaqueTokenService.issue(userProfile.getUserId(), userProfile.getUserType());
        return new GoogleLoginResponse(userResult.isNewMember(), tokens, new UserProfileResponse(userProfile));
    }

    public RefreshTokenResponse refresh(String refreshToken) {
        return new RefreshTokenResponse(opaqueTokenService.refresh(refreshToken));
    }

    public void logout(String accessToken, String refreshToken, String fcmToken) {
        if (fcmToken != null && !fcmToken.trim().isEmpty()) {
            deactivateDevice(accessToken, fcmToken);
        }
        opaqueTokenService.revoke(accessToken, refreshToken);
    }

    /** access token이 이미 만료·무효라면 어느 계정의 기기인지 알 수 없으므로 매핑 해제 없이 로그아웃만 진행한다. */
    private void deactivateDevice(String accessToken, String fcmToken) {
        try {
            AuthenticatedUser user = opaqueTokenService.authenticate(accessToken);
            deviceService.deactivateByToken(user.getUserId(), fcmToken);
        } catch (InvalidOpaqueTokenException exception) {
            // 무시: 디바이스 해제 없이 로그아웃 절차를 계속한다.
        }
    }

}
