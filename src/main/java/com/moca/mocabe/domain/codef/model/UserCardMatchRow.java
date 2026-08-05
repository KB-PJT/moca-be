package com.moca.mocabe.domain.codef.model;

/** 승인내역을 사용자 보유카드에 매칭하기 위한 최소 정보다. cardNo는 마스킹된 CODEF 카드번호(예 943646******1069)다. */
public record UserCardMatchRow(
        String userCardId,
        String cardName,
        String cardNo
) {
}
