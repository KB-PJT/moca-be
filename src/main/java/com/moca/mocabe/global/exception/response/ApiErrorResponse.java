package com.moca.mocabe.global.exception.response;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** API 실패 응답의 공통 형식이다. */
public final class ApiErrorResponse {

    private final boolean success = false;
    private final Object data = null;
    private final ErrorDetail error;

    private ApiErrorResponse(String code, String message, Map<String, String> fields) {
        this.error = new ErrorDetail(code, message, fields);
    }

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(code, message, Collections.<String, String>emptyMap());
    }

    public static ApiErrorResponse of(String code, String message, Map<String, String> fields) {
        return new ApiErrorResponse(code, message, fields);
    }

    public boolean isSuccess() {
        return success;
    }

    public Object getData() {
        return data;
    }

    public ErrorDetail getError() {
        return error;
    }

    /** 클라이언트가 분기 처리할 오류 정보다. */
    public static final class ErrorDetail {

        private final String code;
        private final String message;
        private final Map<String, String> fields;

        private ErrorDetail(String code, String message, Map<String, String> fields) {
            this.code = code;
            this.message = message;
            this.fields = Collections.unmodifiableMap(new LinkedHashMap<String, String>(fields));
        }

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }

        public Map<String, String> getFields() {
            return fields;
        }
    }
}
