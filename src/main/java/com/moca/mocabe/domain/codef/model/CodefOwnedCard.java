package com.moca.mocabe.domain.codef.model;

/** CODEF 개인 보유카드 조회 응답에서 사용하는 내부 모델이다. 카드번호는 응답 DTO나 로그로 내보내지 않는다. */
public record CodefOwnedCard(
        String cardName,
        String cardNumber,
        String cardType,
        String imageUrl
) {
}
