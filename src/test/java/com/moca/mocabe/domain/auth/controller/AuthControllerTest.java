package com.moca.mocabe.domain.auth.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.moca.mocabe.domain.auth.dto.GoogleLoginRequest;
import com.moca.mocabe.domain.auth.dto.GoogleLoginResponse;
import com.moca.mocabe.domain.auth.dto.RefreshTokenResponse;
import com.moca.mocabe.domain.auth.service.AuthApplicationService;
import com.moca.mocabe.domain.user.dto.UserProfileResponse;
import com.moca.mocabe.domain.user.model.UserProfile;
import com.moca.mocabe.global.auth.OpaqueTokenPair;
import com.moca.mocabe.global.auth.OpaqueTokenPolicy;
import com.moca.mocabe.global.auth.RefreshCookiePolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

class AuthControllerTest {

    @Test
    @DisplayName("외부 Tomcat 컨텍스트 경로를 포함해 local refresh cookie를 설정한다")
    void setsRefreshCookieForContextPath() {
        AuthApplicationService authApplicationService = org.mockito.Mockito.mock(AuthApplicationService.class);
        AuthController controller = new AuthController(authApplicationService, new OpaqueTokenPolicy(1800, 1209600),
                new RefreshCookiePolicy(false));
        GoogleLoginRequest request = new GoogleLoginRequest();
        request.setCode("google-code");
        request.setCodeVerifier("code-verifier");
        request.setRedirectUri("http://localhost:5173/auth/callback");
        when(authApplicationService.login("google-code", "code-verifier", "http://localhost:5173/auth/callback"))
                .thenReturn(loginResponse());
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setContextPath("/moca-be");

        String setCookie = controller.googleLogin(request, servletRequest)
                .getHeaders().getFirst(HttpHeaders.SET_COOKIE);

        assertTrue(setCookie.contains("Path=/moca-be/api/v1/auth"));
        assertFalse(setCookie.contains("Secure"));
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("SameSite=Lax"));
    }

    @Test
    @DisplayName("refresh와 logout은 refresh cookie를 갱신 또는 만료시키고 인증 서비스에 위임한다")
    void refreshesAndLogsOut() {
        AuthApplicationService authApplicationService = org.mockito.Mockito.mock(AuthApplicationService.class);
        AuthController controller = new AuthController(authApplicationService, new OpaqueTokenPolicy(1800, 1209600),
                new RefreshCookiePolicy(true));
        when(authApplicationService.refresh("refresh"))
                .thenReturn(new RefreshTokenResponse(new OpaqueTokenPair("new-access", "new-refresh", 1800)));
        MockHttpServletRequest request = new MockHttpServletRequest();

        String refreshedCookie = controller.refresh("refresh", request).getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        String expiredCookie = controller.logout("Bearer access", "refresh", request)
                .getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        controller.logout("Basic ignored", null, request);

        assertTrue(refreshedCookie.contains("new-refresh"));
        assertTrue(refreshedCookie.contains("Secure"));
        assertTrue(refreshedCookie.contains("SameSite=None"));
        assertTrue(expiredCookie.contains("Max-Age=0"));
        verify(authApplicationService).logout("access", "refresh");
        verify(authApplicationService).logout(null, null);
    }

    private GoogleLoginResponse loginResponse() {
        UserProfile userProfile = new UserProfile();
        userProfile.setUserId("01980d6a-5c0c-7aaf-9b85-010203040506");
        userProfile.setNickname("모카");
        userProfile.setUserType("user");
        return new GoogleLoginResponse(false, new OpaqueTokenPair("access", "refresh", 1800),
                new UserProfileResponse(userProfile));
    }
}
