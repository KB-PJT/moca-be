package com.moca.mocabe.domain.auth.controller;

import com.moca.mocabe.domain.auth.dto.GoogleLoginRequest;
import com.moca.mocabe.domain.auth.dto.GoogleLoginResponse;
import com.moca.mocabe.domain.auth.dto.LogoutRequest;
import com.moca.mocabe.domain.auth.dto.RefreshTokenResponse;
import com.moca.mocabe.domain.auth.service.AuthApplicationService;
import com.moca.mocabe.domain.user.dto.SuccessResponse;
import com.moca.mocabe.global.auth.OpaqueTokenPolicy;
import com.moca.mocabe.global.auth.RefreshCookiePolicy;
import com.moca.mocabe.global.response.ApiResponse;
import javax.validation.Valid;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** PWA의 Google PKCE 완료 뒤 MOCA opaque 세션을 관리하는 API다. */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String REFRESH_COOKIE = "moca_refresh";

    private final AuthApplicationService authApplicationService;
    private final OpaqueTokenPolicy opaqueTokenPolicy;
    private final RefreshCookiePolicy refreshCookiePolicy;

    public AuthController(AuthApplicationService authApplicationService, OpaqueTokenPolicy opaqueTokenPolicy,
                          RefreshCookiePolicy refreshCookiePolicy) {
        this.authApplicationService = authApplicationService;
        this.opaqueTokenPolicy = opaqueTokenPolicy;
        this.refreshCookiePolicy = refreshCookiePolicy;
    }

    @PostMapping("/google/login")
    public ResponseEntity<ApiResponse<GoogleLoginResponse>> googleLogin(
            @Valid @RequestBody GoogleLoginRequest request, HttpServletRequest servletRequest) {
        GoogleLoginResponse response = authApplicationService.login(
                request.getCode(), request.getCodeVerifier(), request.getRedirectUri());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(response, servletRequest.getContextPath()).toString())
                .body(ApiResponse.success(response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshTokenResponse>> refresh(
            @CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletRequest servletRequest) {
        RefreshTokenResponse response = authApplicationService.refresh(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(response, servletRequest.getContextPath()).toString())
                .body(ApiResponse.success(response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<SuccessResponse>> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken,
            @Valid @RequestBody(required = false) LogoutRequest request,
            HttpServletRequest servletRequest) {
        String fcmToken = request == null ? null : request.getFcmToken();
        authApplicationService.logout(extractBearerToken(authorization), refreshToken, fcmToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredRefreshCookie(servletRequest.getContextPath()).toString())
                .body(ApiResponse.success(new SuccessResponse(true)));
    }

    private ResponseCookie refreshCookie(GoogleLoginResponse response, String contextPath) {
        return refreshCookie(response.getRefreshToken(), opaqueTokenPolicy.getRefreshTokenTtlSeconds(), contextPath);
    }

    private ResponseCookie refreshCookie(RefreshTokenResponse response, String contextPath) {
        return refreshCookie(response.getRefreshToken(), opaqueTokenPolicy.getRefreshTokenTtlSeconds(), contextPath);
    }

    private ResponseCookie refreshCookie(String refreshToken, long maxAge, String contextPath) {
        return ResponseCookie.from(REFRESH_COOKIE, refreshToken)
                .httpOnly(true)
                .secure(refreshCookiePolicy.isSecure())
                .sameSite(refreshCookiePolicy.getSameSite())
                .path(refreshCookiePath(contextPath))
                .maxAge(maxAge)
                .build();
    }

    private ResponseCookie expiredRefreshCookie(String contextPath) {
        return refreshCookie("", 0, contextPath);
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring("Bearer ".length());
    }

    private String refreshCookiePath(String contextPath) {
        return (contextPath == null ? "" : contextPath) + "/api/v1/auth";
    }
}
