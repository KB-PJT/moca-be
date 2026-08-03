package com.moca.mocabe.domain.codef.model;

/** 특정 연동에 이미 적재된 보유카드 행. 활성화·옵션 검증에 필요한 최소 정보만 담는다. */
public record LinkedCardRow(
        String userCardId,
        String cardId
) {
}
