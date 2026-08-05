package com.moca.mocabe.domain.codef.model;

/**
 * 승인내역 조회에 필요한 사용자별 CODEF 연동 정보다. birthDateEnc는 조회 시 복호화해 사용한다.
 * issuerId는 이 연동이 속한 카드사로, 승인내역 카드 매칭 시 다른 카드사의 보유카드가 후보에
 * 섞이지 않도록 경계를 긋는 데 쓰인다.
 */
public record CodefConnection(
        String connectedId,
        String institutionCode,
        String issuerId,
        byte[] birthDateEnc
) {
}
