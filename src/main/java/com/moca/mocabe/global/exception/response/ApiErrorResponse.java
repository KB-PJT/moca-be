package com.moca.mocabe.global.exception.response;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** API 실패 응답의 공통 형식이다. */
public final class ApiErrorResponse {

    private final boolean success = false;
    private final Object data = null;
    private final ErrorDetail error;

    private ApiErrorResponse(String code, String message, Map<String, String> fields,
                             List<CardFieldError> cards) {
        this.error = new ErrorDetail(code, message, fields, cards);
    }

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(code, message, Collections.<String, String>emptyMap(),
                Collections.<CardFieldError>emptyList());
    }

    public static ApiErrorResponse of(String code, String message, Map<String, String> fields) {
        return new ApiErrorResponse(code, message, fields, Collections.<CardFieldError>emptyList());
    }

    /** 여러 카드를 한 번에 다루는 요청에서, 항목(카드)별로 검증 메시지를 구분해 내려줄 때 쓴다. */
    public static ApiErrorResponse ofCards(String code, String message, List<CardFieldError> cards) {
        return new ApiErrorResponse(code, message, Collections.<String, String>emptyMap(), cards);
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

    /** 카드 한 건에 대한 필드별 검증 메시지다(예 cardNo/cardPassword). */
    public static final class CardFieldError {

        private final String userCardId;
        private final Map<String, String> fields;

        public CardFieldError(String userCardId, Map<String, String> fields) {
            this.userCardId = userCardId;
            this.fields = Collections.unmodifiableMap(new LinkedHashMap<String, String>(fields));
        }

        public String getUserCardId() {
            return userCardId;
        }

        public Map<String, String> getFields() {
            return fields;
        }
    }

    /** 클라이언트가 분기 처리할 오류 정보다. */
    public static final class ErrorDetail {

        private final String code;
        private final String message;
        private final Map<String, String> fields;
        private final List<CardFieldError> cards;

        private ErrorDetail(String code, String message, Map<String, String> fields, List<CardFieldError> cards) {
            this.code = code;
            this.message = message;
            this.fields = Collections.unmodifiableMap(new LinkedHashMap<String, String>(fields));
            this.cards = Collections.unmodifiableList(cards);
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

        /** CARD_CREDENTIAL_REQUIRED처럼 여러 카드를 한 번에 다루는 오류가 아니면 빈 배열이다. */
        public List<CardFieldError> getCards() {
            return cards;
        }
    }
}
