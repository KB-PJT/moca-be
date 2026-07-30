package com.moca.mocabe.domain.auth.service;

import com.moca.mocabe.domain.auth.dto.GoogleLoginResponse;
import com.moca.mocabe.domain.auth.dto.RefreshTokenResponse;
import com.moca.mocabe.domain.user.dto.UserProfileResponse;
import com.moca.mocabe.domain.user.mapper.UserMapper;
import com.moca.mocabe.domain.user.model.UserProfile;
import com.moca.mocabe.global.auth.GoogleIdTokenClaims;
import com.moca.mocabe.global.auth.GoogleIdTokenVerifier;
import com.moca.mocabe.global.auth.OpaqueTokenPair;
import com.moca.mocabe.global.auth.OpaqueTokenService;
import com.moca.mocabe.global.exception.user.UserNotFoundException;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Google 신원 검증 후 MOCA opaque 세션을 발급하는 인증 유스케이스다. */
public class AuthApplicationService {

    private static final String DEFAULT_NICKNAME = "MOCA 회원";

    private final UserMapper userMapper;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final OpaqueTokenService opaqueTokenService;

    public AuthApplicationService(UserMapper userMapper,
                                  GoogleIdTokenVerifier googleIdTokenVerifier,
                                  OpaqueTokenService opaqueTokenService) {
        this.userMapper = userMapper;
        this.googleIdTokenVerifier = googleIdTokenVerifier;
        this.opaqueTokenService = opaqueTokenService;
    }

    @Transactional
    public GoogleLoginResponse login(String idToken) {
        GoogleIdTokenClaims claims = googleIdTokenVerifier.verify(idToken);
        UserProfile userProfile = userMapper.findProfileByGoogleSubject(claims.getSubject());
        boolean newMember = userProfile == null;
        if (newMember) {
            String userId = UUID.randomUUID().toString();
            userMapper.insertGoogleUser(userId, claims.getSubject(), claims.getEmail(), nickname(claims.getName()));
            userProfile = userMapper.findProfileById(userId);
        } else {
            userProfile = userMapper.findProfileById(userProfile.getUserId());
        }
        if (userProfile == null) {
            throw new UserNotFoundException();
        }

        OpaqueTokenPair tokens = opaqueTokenService.issue(userProfile.getUserId(), userProfile.getUserType());
        return new GoogleLoginResponse(newMember, tokens, new UserProfileResponse(userProfile));
    }

    public RefreshTokenResponse refresh(String refreshToken) {
        return new RefreshTokenResponse(opaqueTokenService.refresh(refreshToken));
    }

    public void logout(String accessToken, String refreshToken) {
        opaqueTokenService.revoke(accessToken, refreshToken);
    }

    /** 탈퇴한 사용자가 보유한 모든 Redis 세션을 즉시 무효화한다. */
    public void revokeAllSessions(String userId) {
        opaqueTokenService.revokeAll(userId);
    }

    private String nickname(String name) {
        if (name == null || name.trim().isEmpty()) {
            return DEFAULT_NICKNAME;
        }
        return name.trim().length() > 50 ? name.trim().substring(0, 50) : name.trim();
    }

}
