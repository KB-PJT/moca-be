package com.moca.mocabe.global.exception.security;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moca.mocabe.global.exception.response.ApiErrorResponseWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

class JsonSecurityErrorHandlerTest {

    private final ApiErrorResponseWriter writer = new ApiErrorResponseWriter(new ObjectMapper());

    @Test
    @DisplayName("미인증 Security 요청은 HTML 대신 공통 JSON 401 응답을 반환한다")
    void returnsJsonForUnauthorizedRequest() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JsonAuthenticationEntryPoint(writer).commence(new MockHttpServletRequest(), response,
                new InsufficientAuthenticationException("missing token"));

        assertTrue(response.getContentType().startsWith("application/json"));
        assertTrue(response.getContentAsString().contains("\"code\":\"AUTHENTICATION_REQUIRED\""));
        org.junit.jupiter.api.Assertions.assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("권한 부족 Security 요청은 HTML 대신 공통 JSON 403 응답을 반환한다")
    void returnsJsonForForbiddenRequest() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JsonAccessDeniedHandler(writer).handle(new MockHttpServletRequest(), response,
                new AccessDeniedException("forbidden"));

        assertTrue(response.getContentType().startsWith("application/json"));
        assertTrue(response.getContentAsString().contains("\"code\":\"ACCESS_DENIED\""));
        org.junit.jupiter.api.Assertions.assertEquals(403, response.getStatus());
    }
}
