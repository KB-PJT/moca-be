package com.moca.mocabe.domain.codef.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 카드사가 요구하는 카드번호/비밀번호가 저장돼 있지 않은 카드를 활성화하려 할 때 발생한다.
 * 계정 생성 단계의 {@link CodefCredentialRequiredException}과 달리, 특정 카드(user_card_id)에
 * 대한 것이라 PATCH /card-links/cards/{userCardId}/credentials로 값을 채운 뒤 다시 활성화해야 한다.
 */
public class CardCredentialRequiredException extends RuntimeException {

    private final Map<String, String> fields;

    public CardCredentialRequiredException(Map<String, String> fields) {
        super("카드 활성화에 필요한 카드번호/비밀번호가 없습니다.");
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    public Map<String, String> getFields() {
        return fields;
    }
}
