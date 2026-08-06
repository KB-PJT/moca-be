package com.moca.mocabe.domain.codef.model;

/**
 * 특정 연동에 이미 적재된 보유카드 행. 활성화·옵션 검증에 필요한 최소 정보만 담는다.
 * requiresCardNo/requiresCardPassword는 이 카드의 카드사 정책이고, hasCardNumber/hasCardPassword는
 * 이 카드에 실제로 카드번호/비밀번호가 저장돼 있는지(card_number_enc/card_password_enc IS NOT NULL)다.
 * 카드사가 요구하는데 값이 없으면 활성화할 수 없다.
 */
public record LinkedCardRow(
        String userCardId,
        String cardId,
        boolean requiresCardNo,
        boolean requiresCardPassword,
        boolean hasCardNumber,
        boolean hasCardPassword
) {
}
