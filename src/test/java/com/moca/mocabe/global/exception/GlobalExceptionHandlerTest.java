package com.moca.mocabe.global.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.moca.mocabe.global.exception.auth.AuthenticationRequiredException;
import com.moca.mocabe.global.exception.auth.InvalidGoogleIdTokenException;
import com.moca.mocabe.global.exception.auth.InvalidOpaqueTokenException;
import com.moca.mocabe.global.exception.response.ApiErrorResponse;
import com.moca.mocabe.global.exception.user.UserNotFoundException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.Collections;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("데이터베이스 연결 실패는 세부 정보 없이 공통 JSON 503 응답으로 변환한다")
    void handlesDataAccessFailure() {
        ResponseEntity<ApiErrorResponse> response = handler.handleDataAccess(
                new CannotGetJdbcConnectionException("password should not be exposed", new SQLException()));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("DATA_STORE_UNAVAILABLE", response.getBody().getError().getCode());
        assertEquals("데이터 저장소에 일시적으로 연결할 수 없습니다.",
                response.getBody().getError().getMessage());
    }

    @Test
    @DisplayName("데이터 무결성 위반은 충돌 응답으로 변환한다")
    void handlesDataIntegrityViolation() {
        ResponseEntity<ApiErrorResponse> response = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("duplicate"));

        assertError(response, HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION");
    }

    @Test
    @DisplayName("예상하지 못한 오류는 공통 JSON 500 응답으로 변환한다")
    void handlesUnexpectedFailure() {
        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpected(new IllegalStateException("internal"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_SERVER_ERROR", response.getBody().getError().getCode());
    }

    @Test
    @DisplayName("인증·토큰·사용자 오류를 상태별 공통 오류 코드로 변환한다")
    void handlesAuthenticationAndUserErrors() {
        assertError(handler.handleAuthenticationRequired(new AuthenticationRequiredException()),
                HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED");
        assertError(handler.handleInvalidToken(new InvalidGoogleIdTokenException()),
                HttpStatus.UNAUTHORIZED, "INVALID_TOKEN");
        assertError(handler.handleInvalidToken(new InvalidOpaqueTokenException()),
                HttpStatus.UNAUTHORIZED, "INVALID_TOKEN");
        assertError(handler.handleUserNotFound(new UserNotFoundException()), HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
    }

    @Test
    @DisplayName("요청 형식과 필수 입력 오류를 400 응답으로 변환한다")
    void handlesInvalidRequestErrors() throws Exception {
        assertError(handler.handleUnreadableBody(new HttpMessageNotReadableException("invalid",
                        new MockHttpInputMessage(new byte[0]))),
                HttpStatus.BAD_REQUEST, "INVALID_REQUEST_BODY");
        assertError(handler.handleMissingParameter(new MissingServletRequestParameterException("page", "int")),
                HttpStatus.BAD_REQUEST, "MISSING_PARAMETER");
        assertError(handler.handleMissingHeader(new MissingRequestHeaderException("Authorization", methodParameter())),
                HttpStatus.BAD_REQUEST, "MISSING_HEADER");
        assertError(handler.handleTypeMismatch(new MethodArgumentTypeMismatchException("wrong", Integer.class,
                        "page", methodParameter(), new IllegalArgumentException())),
                HttpStatus.BAD_REQUEST, "INVALID_PARAMETER");
    }

    @Test
    @DisplayName("제약 조건 오류는 필드 메시지를 포함한 400 응답으로 변환한다")
    @SuppressWarnings("unchecked")
    void handlesConstraintViolation() {
        ConstraintViolation<Object> violation = org.mockito.Mockito.mock(ConstraintViolation.class);
        Path path = org.mockito.Mockito.mock(Path.class);
        org.mockito.Mockito.when(path.toString()).thenReturn("nickname");
        org.mockito.Mockito.when(violation.getPropertyPath()).thenReturn(path);
        org.mockito.Mockito.when(violation.getMessage()).thenReturn("닉네임은 필수입니다.");

        ResponseEntity<ApiErrorResponse> response = handler.handleConstraintViolation(
                new ConstraintViolationException(Collections.singleton(violation)));

        assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertEquals("닉네임은 필수입니다.", response.getBody().getError().getFields().get("nickname"));
    }

    @Test
    @DisplayName("HTTP 상태 오류를 대응하는 공통 오류 코드로 변환한다")
    void handlesHttpStatusErrors() throws Exception {
        assertError(handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("POST")),
                HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED");
        assertError(handler.handleMediaTypeNotSupported(
                        new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN.toString())),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE");
        assertError(handler.handleMediaTypeNotAcceptable(new HttpMediaTypeNotAcceptableException("invalid")),
                HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE");
        assertError(handler.handleNoHandler(new NoHandlerFoundException("GET", "/missing", new HttpHeaders())),
                HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
    }

    private void assertError(ResponseEntity<ApiErrorResponse> response, HttpStatus status, String code) {
        assertEquals(status, response.getStatusCode());
        assertEquals(code, response.getBody().getError().getCode());
    }

    private MethodParameter methodParameter() throws Exception {
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("sample", String.class);
        return new MethodParameter(method, 0);
    }

    @SuppressWarnings("unused")
    private void sample(String value) {
    }
}
