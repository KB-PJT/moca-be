package com.moca.mocabe.domain.codef.model;

/**
 * 승인내역을 사용자 보유카드에 매칭하기 위한 최소 정보다. cardNo는 마스킹된 CODEF 카드번호(예 943646******1069)다.
 * issuerId는 승인이 발생한 카드사 연동과 같은 카드사인 카드만 매칭 후보로 좁히는 데 쓰인다(다른 카드사의
 * 동명 카드나 우연히 겹치는 마스킹 카드번호로 오매칭되는 것을 방지).
 */
public record UserCardMatchRow(
        String userCardId,
        String issuerId,
        String cardName,
        String cardNo
) {
}
