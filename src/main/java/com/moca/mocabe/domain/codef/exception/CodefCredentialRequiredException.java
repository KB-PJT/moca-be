package com.moca.mocabe.domain.codef.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 카드사의 CODEF 연동에 필요한 자격정보가 누락됐을 때 발생한다. */
public class CodefCredentialRequiredException extends RuntimeException {

    private final Map<String, String> fields;

    public CodefCredentialRequiredException(Map<String, String> fields) {
        super("카드사 연동에 필요한 정보가 부족합니다.");
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<String, String>(fields));
    }

    public Map<String, String> getFields() {
        return fields;
    }
}
