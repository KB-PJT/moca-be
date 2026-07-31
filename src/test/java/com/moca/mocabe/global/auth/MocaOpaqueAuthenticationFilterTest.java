package com.moca.mocabe.global.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moca.mocabe.global.exception.auth.InvalidOpaqueTokenException;
import com.moca.mocabe.global.exception.response.ApiErrorResponseWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class MocaOpaqueAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("유효하지 않은 access token은 HTML이 아닌 JSON 401로 차단한다")
    void rejectsInvalidTokenWithJson() throws Exception {
        OpaqueTokenService tokenService = mock(OpaqueTokenService.class);
        doThrow(new InvalidOpaqueTokenException()).when(tokenService).authenticate("invalid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        javax.servlet.FilterChain filterChain = mock(javax.servlet.FilterChain.class);

        filter(tokenService).doFilter(request("invalid"), response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("INVALID_TOKEN"));
        verifyNoInteractions(filterChain);
    }

    @Test
    @DisplayName("Redis 장애 시 인증을 통과시키지 않고 JSON 503을 반환한다")
    void rejectsWhenRedisIsUnavailable() throws Exception {
        OpaqueTokenService tokenService = mock(OpaqueTokenService.class);
        doThrow(new DataAccessResourceFailureException("redis unavailable"))
                .when(tokenService).authenticate("access");
        MockHttpServletResponse response = new MockHttpServletResponse();
        javax.servlet.FilterChain filterChain = mock(javax.servlet.FilterChain.class);

        filter(tokenService).doFilter(request("access"), response, filterChain);

        assertEquals(503, response.getStatus());
        assertTrue(response.getContentAsString().contains("DATA_STORE_UNAVAILABLE"));
        verifyNoInteractions(filterChain);
    }

    @Test
    @DisplayName("유효한 access token은 Security Context에 사용자 식별자를 저장하고 다음 필터로 전달한다")
    void authenticatesValidToken() throws Exception {
        OpaqueTokenService tokenService = mock(OpaqueTokenService.class);
        when(tokenService.authenticate("access"))
                .thenReturn(new AuthenticatedUser("01980d6a-5c0c-7aaf-9b85-010203040506", "user"));
        javax.servlet.FilterChain filterChain = mock(javax.servlet.FilterChain.class);

        filter(tokenService).doFilter(request("access"), new MockHttpServletResponse(), filterChain);

        MocaUserPrincipal principal = (MocaUserPrincipal) org.springframework.security.core.context
                .SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertEquals("01980d6a-5c0c-7aaf-9b85-010203040506", principal.getUserId());
        verify(filterChain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Bearer token이 없거나 인증 API 요청이면 인증 필터를 건너뛴다")
    void skipsMissingTokenAndAuthenticationEndpoint() throws Exception {
        OpaqueTokenService tokenService = mock(OpaqueTokenService.class);
        javax.servlet.FilterChain filterChain = mock(javax.servlet.FilterChain.class);
        MockHttpServletRequest missingTokenRequest = new MockHttpServletRequest("GET", "/api/v1/me");

        filter(tokenService).doFilter(missingTokenRequest, new MockHttpServletResponse(), filterChain);
        MockHttpServletRequest authRequest = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
        authRequest.addHeader("Authorization", "Bearer access");
        filter(tokenService).doFilter(authRequest, new MockHttpServletResponse(), filterChain);

        verify(filterChain, org.mockito.Mockito.times(2))
                .doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(tokenService);
    }

    @Test
    @DisplayName("UUID 형식이 아닌 세션 사용자 식별자는 JSON 401로 차단한다")
    void rejectsMalformedSessionUserId() throws Exception {
        OpaqueTokenService tokenService = mock(OpaqueTokenService.class);
        when(tokenService.authenticate("access")).thenReturn(new AuthenticatedUser("not-a-uuid", "user"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter(tokenService).doFilter(request("access"), response, mock(javax.servlet.FilterChain.class));

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("INVALID_TOKEN"));
    }

    private MocaOpaqueAuthenticationFilter filter(OpaqueTokenService tokenService) {
        return new MocaOpaqueAuthenticationFilter(tokenService,
                new ApiErrorResponseWriter(new ObjectMapper()));
    }

    private MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
