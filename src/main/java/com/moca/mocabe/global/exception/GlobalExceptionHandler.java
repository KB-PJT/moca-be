package com.moca.mocabe.global.exception;

import com.moca.mocabe.domain.codef.exception.CardAlreadyLinkedException;
import com.moca.mocabe.domain.codef.exception.CodefAccountAlreadyLinkedException;
import com.moca.mocabe.domain.codef.exception.CodefCredentialRequiredException;
import com.moca.mocabe.domain.codef.exception.IssuerNotFoundException;
import com.moca.mocabe.domain.codef.exception.CardLinkNotFoundException;
import com.moca.mocabe.domain.codef.exception.InvalidCardSelectionException;
import com.moca.mocabe.global.exception.auth.AuthenticationRequiredException;
import com.moca.mocabe.global.exception.auth.InvalidOpaqueTokenException;
import com.moca.mocabe.global.auth.GoogleAuthorizationCodeException;
import com.moca.mocabe.global.exception.response.ApiErrorResponse;
import com.moca.mocabe.global.exception.home.InvalidHomeQueryException;
import com.moca.mocabe.global.exception.home.HomeDataNotFoundException;
import com.moca.mocabe.global.exception.user.UserNotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.validation.ConstraintViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.NoHandlerFoundException;

/** Controller와 Service에서 발생한 오류를 공통 JSON 응답으로 변환한다. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = Logger.getLogger(GlobalExceptionHandler.class.getName());

    @ExceptionHandler(AuthenticationRequiredException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationRequired(
            AuthenticationRequiredException exception) {
        return error(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", exception.getMessage());
    }

    @ExceptionHandler({InvalidOpaqueTokenException.class, GoogleAuthorizationCodeException.class})
    public ResponseEntity<ApiErrorResponse> handleInvalidToken(RuntimeException exception) {
        return error(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", exception.getMessage());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleUserNotFound(UserNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(IssuerNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleIssuerNotFound(IssuerNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "ISSUER_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(CardLinkNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleCardLinkNotFound(CardLinkNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "CARD_LINK_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(InvalidCardSelectionException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCardSelection(InvalidCardSelectionException exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_CARD_SELECTION", exception.getMessage());
    }

    @ExceptionHandler(InvalidHomeQueryException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidHomeQuery(InvalidHomeQueryException exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_HOME_QUERY", exception.getMessage());
    }

    @ExceptionHandler(HomeDataNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleHomeDataNotFound(HomeDataNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "HOME_DATA_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(CodefCredentialRequiredException.class)
    public ResponseEntity<ApiErrorResponse> handleCodefCredentialRequired(
            CodefCredentialRequiredException exception) {
        return error(HttpStatus.BAD_REQUEST, "CODEF_CREDENTIAL_REQUIRED",
                exception.getMessage(), exception.getFields());
    }

    @ExceptionHandler(CodefAccountAlreadyLinkedException.class)
    public ResponseEntity<ApiErrorResponse> handleCodefAccountAlreadyLinked(
            CodefAccountAlreadyLinkedException exception) {
        return error(HttpStatus.CONFLICT, "CODEF_ACCOUNT_ALREADY_LINKED", exception.getMessage());
    }

    @ExceptionHandler(CardAlreadyLinkedException.class)
    public ResponseEntity<ApiErrorResponse> handleCardAlreadyLinked(CardAlreadyLinkedException exception) {
        return error(HttpStatus.CONFLICT, "CARD_ALREADY_LINKED", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<String, String>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            fields.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "요청값이 올바르지 않습니다.", fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
        Map<String, String> fields = new LinkedHashMap<String, String>();
        exception.getConstraintViolations().forEach(violation ->
                fields.put(violation.getPropertyPath().toString(), violation.getMessage()));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "요청값이 올바르지 않습니다.", fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_BODY", "요청 본문 형식이 올바르지 않습니다.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(MissingServletRequestParameterException exception) {
        return error(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", "필수 요청 파라미터가 없습니다.");
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingHeader(MissingRequestHeaderException exception) {
        return error(HttpStatus.BAD_REQUEST, "MISSING_HEADER", "필수 요청 헤더가 없습니다.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "요청 파라미터 형식이 올바르지 않습니다.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "지원하지 않는 HTTP 메서드입니다.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException exception) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "지원하지 않는 콘텐츠 타입입니다.");
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaTypeNotAcceptable(
            HttpMediaTypeNotAcceptableException exception) {
        return error(HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE", "지원하지 않는 응답 형식입니다.");
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoHandler(NoHandlerFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        return error(HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION", "데이터 제약 조건을 위반했습니다.");
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiErrorResponse> handleDataAccess(DataAccessException exception) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "DATA_STORE_UNAVAILABLE",
                "데이터 저장소에 일시적으로 연결할 수 없습니다.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        // 처리되지 않은 예외는 클라이언트에 감추되, 원인 추적을 위해 서버 로그에는 스택트레이스를 남긴다.
        LOGGER.log(Level.SEVERE, "처리되지 않은 예외로 500을 반환합니다.", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다.");
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(code, message));
    }

    private ResponseEntity<ApiErrorResponse> error(
            HttpStatus status, String code, String message, Map<String, String> fields) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(code, message, fields));
    }
}
