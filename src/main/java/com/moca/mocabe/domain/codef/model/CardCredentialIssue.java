package com.moca.mocabe.domain.codef.model;

import java.util.Map;

/**
 * 카드 활성화 요청에서 카드번호/비밀번호가 부족한 카드 한 건이다. fields는 필드명(cardNo/cardPassword)과
 * 검증 메시지의 매핑으로, 부족한 항목만 담긴다.
 */
public record CardCredentialIssue(
        String userCardId,
        Map<String, String> fields
) {
}
