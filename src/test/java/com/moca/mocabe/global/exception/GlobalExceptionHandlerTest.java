package com.moca.mocabe.global.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.moca.mocabe.domain.codef.exception.ApprovalSyncFailedException;
import com.moca.mocabe.domain.codef.exception.CardAlreadyLinkedException;
import com.moca.mocabe.domain.codef.exception.CardCredentialRequiredException;
import com.moca.mocabe.domain.codef.exception.CardNumberMismatchException;
import com.moca.mocabe.domain.codef.exception.CodefAccountAlreadyLinkedException;
import com.moca.mocabe.domain.codef.exception.CodefConnectionNotFoundException;
import com.moca.mocabe.domain.codef.exception.CodefCredentialRequiredException;
import com.moca.mocabe.domain.codef.exception.CodefAccountLockedException;
import com.moca.mocabe.domain.codef.exception.CodefInvalidCredentialsException;
import com.moca.mocabe.domain.codef.exception.CodefUnavailableException;
import com.moca.mocabe.domain.codef.exception.InvalidSyncPeriodException;
import com.moca.mocabe.domain.codef.exception.IssuerNotFoundException;
import com.moca.mocabe.domain.codef.exception.PerformanceSyncFailedException;
import com.moca.mocabe.domain.codef.exception.UserCardNotFoundException;
import com.moca.mocabe.global.exception.auth.AuthenticationRequiredException;
import com.moca.mocabe.global.auth.GoogleAuthorizationCodeException;
import com.moca.mocabe.global.exception.auth.InvalidOpaqueTokenException;
import com.moca.mocabe.global.exception.response.ApiErrorResponse;
import com.moca.mocabe.global.exception.user.UserNotFoundException;
import com.moca.mocabe.global.exception.home.InvalidHomeQueryException;
import com.moca.mocabe.global.exception.home.HomeDataNotFoundException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
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
    @DisplayName("데이터 제약 조건 위반은 공통 JSON 409 응답으로 변환한다")
    void handlesDataIntegrityViolation() {
        ResponseEntity<ApiErrorResponse> response = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("duplicate key"));

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
        assertError(handler.handleInvalidToken(new GoogleAuthorizationCodeException()),
                HttpStatus.UNAUTHORIZED, "INVALID_TOKEN");
        assertError(handler.handleInvalidToken(new InvalidOpaqueTokenException()),
                HttpStatus.UNAUTHORIZED, "INVALID_TOKEN");
        assertError(handler.handleUserNotFound(new UserNotFoundException()), HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
    }

    @Test
    @DisplayName("홈 조회 조건 오류를 공통 JSON 400 응답으로 변환한다")
    void handlesInvalidHomeQuery() {
        assertError(handler.handleInvalidHomeQuery(new InvalidHomeQueryException("invalid")),
                HttpStatus.BAD_REQUEST, "INVALID_HOME_QUERY");
    }

    @Test
    @DisplayName("홈 데이터가 없으면 공통 JSON 404 응답으로 변환한다")
    void handlesHomeDataNotFound() {
        assertError(handler.handleHomeDataNotFound(new HomeDataNotFoundException("empty")),
                HttpStatus.NOT_FOUND, "HOME_DATA_NOT_FOUND");
    }

    @Test
    @DisplayName("등록되지 않은 발급사는 식별 가능한 404 오류로 변환한다")
    void handlesIssuerNotFound() {
        String issuerId = "00000000-0000-4000-8000-000000000301";

        ResponseEntity<ApiErrorResponse> response = handler.handleIssuerNotFound(
                new IssuerNotFoundException(issuerId));

        assertError(response, HttpStatus.NOT_FOUND, "ISSUER_NOT_FOUND");
        assertEquals("등록되지 않은 발급사입니다: " + issuerId,
                response.getBody().getError().getMessage());
    }

    @Test
    @DisplayName("카드사 로그인 아이디·비밀번호가 틀리면 재시도 안내가 아니라 식별 가능한 400 오류로 변환한다")
    void handlesCodefInvalidCredentials() {
        ResponseEntity<ApiErrorResponse> response = handler.handleCodefInvalidCredentials(
                new CodefInvalidCredentialsException());

        assertError(response, HttpStatus.BAD_REQUEST, "CODEF_INVALID_CREDENTIALS");
        assertEquals("아이디 또는 비밀번호가 올바르지 않습니다.",
                response.getBody().getError().getMessage());
    }

    @Test
    @DisplayName("비밀번호 오류 횟수 초과로 계정이 잠기면 재시도 안내가 아니라 423 잠김 오류로 변환한다")
    void handlesCodefAccountLocked() {
        ResponseEntity<ApiErrorResponse> response = handler.handleCodefAccountLocked(
                new CodefAccountLockedException());

        assertError(response, HttpStatus.LOCKED, "CODEF_ACCOUNT_LOCKED");
        assertEquals("비밀번호 오류 횟수를 초과해 카드사 계정이 잠겼습니다. 카드사를 통해 잠금을 해제한 뒤 다시 시도해주세요.",
                response.getBody().getError().getMessage());
    }

    @Test
    @DisplayName("보유카드 재조회 시 지정한 기관코드로 연동된 계정이 없으면 식별 가능한 404 오류로 변환한다")
    void handlesCodefConnectionNotFound() {
        ResponseEntity<ApiErrorResponse> response = handler.handleCodefConnectionNotFound(
                new CodefConnectionNotFoundException("0301"));

        assertError(response, HttpStatus.NOT_FOUND, "CODEF_CONNECTION_NOT_FOUND");
        assertEquals("연동된 카드사 계정을 찾을 수 없습니다: 0301",
                response.getBody().getError().getMessage());
    }

    @Test
    @DisplayName("CODEF 상류 타임아웃·연결 실패는 재시도 가능한 503으로 변환한다")
    void handlesCodefUnavailable() {
        ResponseEntity<ApiErrorResponse> response = handler.handleCodefUnavailable(
                new CodefUnavailableException("CODEF 응답이 지연되어 처리하지 못했습니다. 잠시 후 다시 시도해주세요.",
                        new java.net.http.HttpTimeoutException("timeout")));

        assertError(response, HttpStatus.SERVICE_UNAVAILABLE, "CODEF_UNAVAILABLE");
        assertEquals("CODEF 응답이 지연되어 처리하지 못했습니다. 잠시 후 다시 시도해주세요.",
                response.getBody().getError().getMessage());
    }

    @Test
    @DisplayName("승인내역 동기화 실패는 승인내역 전용 코드로 503 오류로 변환한다")
    void handlesApprovalSyncFailed() {
        ResponseEntity<ApiErrorResponse> response = handler.handleApprovalSyncFailed(
                new ApprovalSyncFailedException("승인내역 동기화에 실패했습니다.",
                        new CodefUnavailableException("CODEF 승인내역 조회에 실패했습니다.")));

        assertError(response, HttpStatus.SERVICE_UNAVAILABLE, "APPROVAL_SYNC_FAILED");
        assertEquals("승인내역 동기화에 실패했습니다.", response.getBody().getError().getMessage());
    }

    @Test
    @DisplayName("실적조회 동기화 실패는 실적조회 전용 코드로 503 오류로 변환한다")
    void handlesPerformanceSyncFailed() {
        ResponseEntity<ApiErrorResponse> response = handler.handlePerformanceSyncFailed(
                new PerformanceSyncFailedException("하나카드는 실적조회를 지원하지 않는 카드사입니다."));

        assertError(response, HttpStatus.SERVICE_UNAVAILABLE, "PERFORMANCE_SYNC_FAILED");
        assertEquals("하나카드는 실적조회를 지원하지 않는 카드사입니다.", response.getBody().getError().getMessage());
    }

    @Test
    @DisplayName("잘못된 동기화 조회 기간은 400 오류로 변환한다")
    void handlesInvalidSyncPeriod() {
        ResponseEntity<ApiErrorResponse> response = handler.handleInvalidSyncPeriod(
                new InvalidSyncPeriodException("조회 시작일이 종료일보다 늦을 수 없습니다."));

        assertError(response, HttpStatus.BAD_REQUEST, "INVALID_SYNC_PERIOD");
        assertEquals("조회 시작일이 종료일보다 늦을 수 없습니다.",
                response.getBody().getError().getMessage());
    }

    @Test
    @DisplayName("CODEF 필수정보 누락과 중복 연동을 식별 가능한 오류로 변환한다")
    void handlesCodefCredentialErrors() {
        Map<String, String> fields = new LinkedHashMap<String, String>();
        fields.put("cardNo", "카드번호는 필수입니다.");

        ResponseEntity<ApiErrorResponse> requiredResponse = handler.handleCodefCredentialRequired(
                new CodefCredentialRequiredException(fields));
        ResponseEntity<ApiErrorResponse> duplicateResponse = handler.handleCodefAccountAlreadyLinked(
                new CodefAccountAlreadyLinkedException());
        ResponseEntity<ApiErrorResponse> duplicateCardResponse = handler.handleCardAlreadyLinked(
                new CardAlreadyLinkedException(new RuntimeException("cause")));

        assertError(requiredResponse, HttpStatus.BAD_REQUEST, "CODEF_CREDENTIAL_REQUIRED");
        assertEquals("카드번호는 필수입니다.", requiredResponse.getBody().getError().getFields().get("cardNo"));
        assertError(duplicateResponse, HttpStatus.CONFLICT, "CODEF_ACCOUNT_ALREADY_LINKED");
        assertError(duplicateCardResponse, HttpStatus.CONFLICT, "CARD_ALREADY_LINKED");
    }

    @Test
    @DisplayName("카드 활성화·카드정보 추가 입력 관련 오류를 식별 가능한 오류로 변환한다")
    void handlesCardCredentialErrors() {
        Map<String, String> fields = new LinkedHashMap<String, String>();
        fields.put("cardNo", "카드번호가 필요합니다.");

        ResponseEntity<ApiErrorResponse> requiredResponse = handler.handleCardCredentialRequired(
                new CardCredentialRequiredException(fields));
        ResponseEntity<ApiErrorResponse> mismatchResponse = handler.handleCardNumberMismatch(
                new CardNumberMismatchException());
        ResponseEntity<ApiErrorResponse> notFoundResponse = handler.handleUserCardNotFound(
                new UserCardNotFoundException());

        assertError(requiredResponse, HttpStatus.BAD_REQUEST, "CARD_CREDENTIAL_REQUIRED");
        assertEquals("카드번호가 필요합니다.", requiredResponse.getBody().getError().getFields().get("cardNo"));
        assertError(mismatchResponse, HttpStatus.BAD_REQUEST, "CARD_NUMBER_MISMATCH");
        assertError(notFoundResponse, HttpStatus.NOT_FOUND, "USER_CARD_NOT_FOUND");
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
