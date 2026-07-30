package com.moca.mocabe.global.exception.security;

import com.moca.mocabe.global.exception.response.ApiErrorResponseWriter;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/** 권한이 부족한 Security 필터 단계의 403 응답을 JSON으로 반환한다. */
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final ApiErrorResponseWriter errorResponseWriter;

    public JsonAccessDeniedHandler(ApiErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        errorResponseWriter.write(response, HttpServletResponse.SC_FORBIDDEN, "ACCESS_DENIED",
                "접근 권한이 없습니다.");
    }
}
