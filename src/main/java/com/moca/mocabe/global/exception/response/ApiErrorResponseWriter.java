package com.moca.mocabe.global.exception.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

/** DispatcherServlet 밖의 Security 오류도 공통 JSON 형식으로 기록한다. */
public class ApiErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public ApiErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiErrorResponse.of(code, message));
    }
}
