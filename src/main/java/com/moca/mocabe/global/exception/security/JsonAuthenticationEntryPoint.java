package com.moca.mocabe.global.exception.security;

import com.moca.mocabe.global.exception.response.ApiErrorResponseWriter;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/** 인증 정보가 없거나 유효하지 않은 Security 필터 단계의 401 응답을 JSON으로 반환한다. */
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ApiErrorResponseWriter errorResponseWriter;

    public JsonAuthenticationEntryPoint(ApiErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authenticationException) throws IOException, ServletException {
        errorResponseWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                "인증이 필요합니다.");
    }
}
