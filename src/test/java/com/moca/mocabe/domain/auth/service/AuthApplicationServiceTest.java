package com.moca.mocabe.domain.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moca.mocabe.domain.auth.dto.GoogleLoginResponse;
import com.moca.mocabe.domain.user.model.UserProfile;
import com.moca.mocabe.domain.user.service.UserDomainService;
import com.moca.mocabe.global.auth.GoogleIdTokenClaims;
import com.moca.mocabe.global.auth.GoogleIdTokenVerifier;
import com.moca.mocabe.global.auth.OpaqueTokenPair;
import com.moca.mocabe.global.auth.OpaqueTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthApplicationServiceTest {

    private static final String USER_ID = "01980d6a-5c0c-7aaf-9b85-010203040506";
    private static final String GOOGLE_SUBJECT = "google-subject";

    @Mock
    private UserDomainService userDomainService;

    @Mock
    private GoogleIdTokenVerifier googleIdTokenVerifier;

    @Mock
    private OpaqueTokenService opaqueTokenService;

    @InjectMocks
    private AuthApplicationService authApplicationService;

    @Test
    @DisplayName("기존 Google 회원은 검증된 sub로 찾아 MOCA opaque token을 발급한다")
    void logsInExistingGoogleMember() {
        UserProfile user = user();
        when(googleIdTokenVerifier.verify("google-id-token"))
                .thenReturn(new GoogleIdTokenClaims(GOOGLE_SUBJECT, "moca@example.com", "모카"));
        when(userDomainService.findOrCreateGoogleUser(GOOGLE_SUBJECT, "moca@example.com", "모카"))
                .thenReturn(new UserDomainService.GoogleUserResult(user, false));
        when(opaqueTokenService.issue(USER_ID, "user"))
                .thenReturn(new OpaqueTokenPair("access", "refresh", 1800));

        GoogleLoginResponse response = authApplicationService.login("google-id-token");

        assertFalse(response.isNewMember());
        assertEquals("access", response.getAccessToken());
        assertEquals(USER_ID, response.getMember().getUserId());
        verify(opaqueTokenService).issue(USER_ID, "user");
    }

    @Test
    @DisplayName("처음 로그인한 Google 회원은 UUID 사용자 계정을 생성한 뒤 token을 발급한다")
    void createsNewGoogleMember() {
        UserProfile user = user();
        when(googleIdTokenVerifier.verify("google-id-token"))
                .thenReturn(new GoogleIdTokenClaims(GOOGLE_SUBJECT, "moca@example.com", "모카"));
        when(userDomainService.findOrCreateGoogleUser(eq(GOOGLE_SUBJECT), eq("moca@example.com"), anyString()))
                .thenReturn(new UserDomainService.GoogleUserResult(user, true));
        when(opaqueTokenService.issue(USER_ID, "user"))
                .thenReturn(new OpaqueTokenPair("access", "refresh", 1800));

        GoogleLoginResponse response = authApplicationService.login("google-id-token");

        assertTrue(response.isNewMember());
        verify(userDomainService).findOrCreateGoogleUser(eq(GOOGLE_SUBJECT), eq("moca@example.com"), eq("모카"));
        verify(opaqueTokenService).issue(USER_ID, "user");
    }

    @Test
    @DisplayName("이름 정보가 없는 최초 회원은 기본 닉네임으로 생성한다")
    void createsNewMemberWithDefaultNickname() {
        UserProfile user = user();
        when(googleIdTokenVerifier.verify("google-id-token"))
                .thenReturn(new GoogleIdTokenClaims(GOOGLE_SUBJECT, "moca@example.com", " "));
        when(userDomainService.findOrCreateGoogleUser(eq(GOOGLE_SUBJECT), eq("moca@example.com"), anyString()))
                .thenReturn(new UserDomainService.GoogleUserResult(user, true));
        when(opaqueTokenService.issue(USER_ID, "user"))
                .thenReturn(new OpaqueTokenPair("access", "refresh", 1800));

        authApplicationService.login("google-id-token");

        verify(userDomainService).findOrCreateGoogleUser(eq(GOOGLE_SUBJECT), eq("moca@example.com"),
                eq("MOCA 회원"));
    }

    @Test
    @DisplayName("사용자 도메인 서비스의 사용자 조회 오류를 로그인 흐름에 전달한다")
    void propagatesMissingGoogleMember() {
        when(googleIdTokenVerifier.verify("google-id-token"))
                .thenReturn(new GoogleIdTokenClaims(GOOGLE_SUBJECT, "moca@example.com", "모카"));
        when(userDomainService.findOrCreateGoogleUser(eq(GOOGLE_SUBJECT), anyString(), anyString()))
                .thenThrow(new com.moca.mocabe.global.exception.user.UserNotFoundException());
        org.junit.jupiter.api.Assertions.assertThrows(
                com.moca.mocabe.global.exception.user.UserNotFoundException.class,
                () -> authApplicationService.login("google-id-token"));
    }

    @Test
    @DisplayName("refresh와 logout은 opaque token 서비스에 위임한다")
    void refreshesAndLogsOut() {
        when(opaqueTokenService.refresh("refresh"))
                .thenReturn(new OpaqueTokenPair("access", "new-refresh", 1800));

        assertEquals("access", authApplicationService.refresh("refresh").getAccessToken());
        authApplicationService.logout("access", "refresh");

        verify(opaqueTokenService).revoke("access", "refresh");
    }

    private UserProfile user() {
        UserProfile user = new UserProfile();
        user.setUserId(USER_ID);
        user.setNickname("모카");
        user.setEmail("moca@example.com");
        user.setUserType("user");
        return user;
    }
}
