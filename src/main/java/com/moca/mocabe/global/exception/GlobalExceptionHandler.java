package com.moca.mocabe.global.exception;

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
import com.moca.mocabe.domain.codef.exception.IssuerNotFoundException;
import com.moca.mocabe.domain.codef.exception.CardLinkNotFoundException;
import com.moca.mocabe.domain.codef.exception.InvalidCardSelectionException;
import com.moca.mocabe.domain.codef.exception.InvalidSyncPeriodException;
import com.moca.mocabe.domain.codef.exception.PerformanceSyncFailedException;
import com.moca.mocabe.domain.codef.exception.PerformanceUnsupportedException;
import com.moca.mocabe.domain.codef.exception.UserCardNotFoundException;
import com.moca.mocabe.domain.codef.model.CardCredentialIssue;
import com.moca.mocabe.global.exception.auth.AuthenticationRequiredException;
import com.moca.mocabe.global.exception.auth.InvalidOpaqueTokenException;
import com.moca.mocabe.global.exception.benefit.BenefitHistoryNotFoundException;
import com.moca.mocabe.global.exception.benefit.InvalidBenefitHistoryQueryException;
import com.moca.mocabe.global.auth.GoogleAuthorizationCodeException;
import com.moca.mocabe.global.exception.response.ApiErrorResponse;
import com.moca.mocabe.global.exception.home.InvalidHomeQueryException;
import com.moca.mocabe.global.exception.home.HomeDataNotFoundException;
import com.moca.mocabe.global.exception.merchant.InvalidMerchantQueryException;
import com.moca.mocabe.global.exception.merchant.KakaoUnavailableException;
import com.moca.mocabe.global.exception.merchant.MerchantCategoryNotFoundException;
import com.moca.mocabe.global.exception.report.InvalidReportQueryException;
import com.moca.mocabe.global.exception.user.UserNotFoundException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
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

    @ExceptionHandler(UserCardNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleUserCardNotFound(UserCardNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "USER_CARD_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(CodefConnectionNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleCodefConnectionNotFound(
            CodefConnectionNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "CODEF_CONNECTION_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(InvalidCardSelectionException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCardSelection(InvalidCardSelectionException exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_CARD_SELECTION", exception.getMessage());
    }

    @ExceptionHandler(InvalidSyncPeriodException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidSyncPeriod(InvalidSyncPeriodException exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_SYNC_PERIOD", exception.getMessage());
    }

    @ExceptionHandler(InvalidHomeQueryException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidHomeQuery(InvalidHomeQueryException exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_HOME_QUERY", exception.getMessage());
    }

    @ExceptionHandler(InvalidBenefitHistoryQueryException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidBenefitHistoryQuery(
            InvalidBenefitHistoryQueryException exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_BENEFIT_HISTORY_QUERY", exception.getMessage());
    }

    @ExceptionHandler(BenefitHistoryNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleBenefitHistoryNotFound(
            BenefitHistoryNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "BENEFIT_HISTORY_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(HomeDataNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleHomeDataNotFound(HomeDataNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "HOME_DATA_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(InvalidMerchantQueryException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidMerchantQuery(InvalidMerchantQueryException exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_MERCHANT_QUERY", exception.getMessage());
    }

    @ExceptionHandler(InvalidReportQueryException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidReportQuery(
            InvalidReportQueryException exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REPORT_QUERY", exception.getMessage());
    }

    @ExceptionHandler(MerchantCategoryNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleMerchantCategoryNotFound(
            MerchantCategoryNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "MERCHANT_CATEGORY_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(KakaoUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleKakaoUnavailable(KakaoUnavailableException exception) {
        // 상류(카카오맵) 일시 장애·지연이므로 500이 아니라 재시도 가능한 503으로 안내한다.
        LOGGER.log(Level.WARNING, "카카오맵 연동 상류 오류로 503을 반환합니다. " + describeException(exception));
        return error(HttpStatus.SERVICE_UNAVAILABLE, "KAKAO_UNAVAILABLE", exception.getMessage());
    }

    @ExceptionHandler(CodefCredentialRequiredException.class)
    public ResponseEntity<ApiErrorResponse> handleCodefCredentialRequired(
            CodefCredentialRequiredException exception) {
        return error(HttpStatus.BAD_REQUEST, "CODEF_CREDENTIAL_REQUIRED",
                exception.getMessage(), exception.getFields());
    }

    @ExceptionHandler(CardCredentialRequiredException.class)
    public ResponseEntity<ApiErrorResponse> handleCardCredentialRequired(
            CardCredentialRequiredException exception) {
        // 여러 카드를 한 번에 활성화하는 요청이면, 어느 카드가 문제인지 콤마로 구분해 userCardId에 담는다.
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("userCardId", exception.getIssues().stream()
                .map(CardCredentialIssue::userCardId)
                .collect(Collectors.joining(",")));
        return error(HttpStatus.BAD_REQUEST, "CARD_CREDENTIAL_REQUIRED", exception.getMessage(), fields);
    }

    @ExceptionHandler(CardNumberMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleCardNumberMismatch(CardNumberMismatchException exception) {
        return error(HttpStatus.BAD_REQUEST, "CARD_NUMBER_MISMATCH", exception.getMessage());
    }

    @ExceptionHandler(CodefInvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleCodefInvalidCredentials(
            CodefInvalidCredentialsException exception) {
        return error(HttpStatus.BAD_REQUEST, "CODEF_INVALID_CREDENTIALS", exception.getMessage());
    }

    @ExceptionHandler(CodefAccountLockedException.class)
    public ResponseEntity<ApiErrorResponse> handleCodefAccountLocked(CodefAccountLockedException exception) {
        // 비밀번호 오류 횟수 초과로 계정 자체가 잠긴 상태라 재시도해도 해결되지 않으므로 423으로 구분한다.
        return error(HttpStatus.LOCKED, "CODEF_ACCOUNT_LOCKED", exception.getMessage());
    }

    @ExceptionHandler(CodefUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleCodefUnavailable(CodefUnavailableException exception) {
        // 상류(CODEF) 일시 장애·지연이므로 500이 아니라 재시도 가능한 503으로 안내한다.
        // 예외 원문(메시지·스택트레이스)은 로그에 남기지 않고, 클래스명만 남긴다(CWE-532).
        LOGGER.log(Level.WARNING, "CODEF 연동 상류 오류로 503을 반환합니다. " + describeException(exception));
        return error(HttpStatus.SERVICE_UNAVAILABLE, "CODEF_UNAVAILABLE", exception.getMessage());
    }

    @ExceptionHandler(ApprovalSyncFailedException.class)
    public ResponseEntity<ApiErrorResponse> handleApprovalSyncFailed(ApprovalSyncFailedException exception) {
        // CODEF 승인내역 조회 실패는 상류 일시 장애이므로 재시도 가능한 503으로 안내한다.
        LOGGER.log(Level.WARNING, "승인내역 동기화 실패로 503을 반환합니다. " + describeException(exception));
        return error(HttpStatus.SERVICE_UNAVAILABLE, "APPROVAL_SYNC_FAILED", exception.getMessage());
    }

    @ExceptionHandler(PerformanceSyncFailedException.class)
    public ResponseEntity<ApiErrorResponse> handlePerformanceSyncFailed(PerformanceSyncFailedException exception) {
        // CODEF 실적조회 호출 자체가 실패한 일시적 상황이므로 재시도 가능한 503으로 안내한다.
        LOGGER.log(Level.WARNING, "실적조회 동기화 실패로 503을 반환합니다. " + describeException(exception));
        return error(HttpStatus.SERVICE_UNAVAILABLE, "PERFORMANCE_SYNC_FAILED", exception.getMessage());
    }

    @ExceptionHandler(PerformanceUnsupportedException.class)
    public ResponseEntity<ApiErrorResponse> handlePerformanceUnsupported(PerformanceUnsupportedException exception) {
        // 카드사 실적조회 미지원 또는 조회 가능 범위 초과는 재시도해도 항상 실패하는 영구 조건이므로 400으로 안내한다.
        return error(HttpStatus.BAD_REQUEST, "PERFORMANCE_UNSUPPORTED", exception.getMessage());
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
        // 어떤 제약을 위반했는지 추적할 수 있도록 원인을 로그로 남기되, SQL 오류 메시지에는 바인딩 값이
        // 포함될 수 있어(CWE-532) 예외 원문 대신 예외/원인 클래스명과 SQLState만 남긴다.
        LOGGER.log(Level.WARNING, "데이터 제약 위반으로 409를 반환합니다. " + describeException(exception));
        return error(HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION", "데이터 제약 조건을 위반했습니다.");
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiErrorResponse> handleDataAccess(DataAccessException exception) {
        // SQL 오류·매핑 오류 등이 503으로 뭉뚱그려지면 원인을 알 수 없어 로그는 남기되, 예외 원문(메시지·
        // 스택트레이스)에는 쿼리·바인딩 값이 담길 수 있어(CWE-532) 클래스명과 SQLState만 남긴다.
        LOGGER.log(Level.WARNING, "데이터 접근 오류로 503을 반환합니다. " + describeException(exception));
        return error(HttpStatus.SERVICE_UNAVAILABLE, "DATA_STORE_UNAVAILABLE",
                "데이터 저장소에 일시적으로 연결할 수 없습니다.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        // 처리되지 않은 예외는 클라이언트에 감추되, 원인 추적을 위해 서버 로그에는 스택트레이스를 남긴다.
        LOGGER.log(Level.SEVERE, "처리되지 않은 예외로 500을 반환합니다.", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다.");
    }

    /**
     * 로그에 안전하게 남길 수 있는 예외 요약을 만든다(CWE-532). 예외/원인의 클래스명과, 원인이
     * SQLException이면 SQLState까지만 담고, 메시지나 스택트레이스처럼 쿼리·바인딩 값이 섞일 수 있는
     * 원문은 포함하지 않는다.
     */
    private String describeException(Throwable exception) {
        StringBuilder description = new StringBuilder(exception.getClass().getSimpleName());
        Throwable cause = exception.getCause();
        if (cause != null) {
            description.append(" <- ").append(cause.getClass().getSimpleName());
            if (cause instanceof SQLException sqlException) {
                description.append(" sqlState=").append(sqlException.getSQLState());
            }
        }
        return description.toString();
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(code, message));
    }

    private ResponseEntity<ApiErrorResponse> error(
            HttpStatus status, String code, String message, Map<String, String> fields) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(code, message, fields));
    }
}
