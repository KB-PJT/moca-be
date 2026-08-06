package com.moca.mocabe.domain.codef.model;

/**
 * CODEF 조회에 필요한 사용자별 연동 정보다. 자격정보 암호문은 조회 시 복호화해 사용한다.
 * TODO(BE): /card-links/cards/sync에서 현대카드 재조회에 필요한 암호문을 추가한 것이므로,
 * 카드사별 필요 필드와 서비스 계층에 노출할 최소 범위를 보안 관점에서 최종 확인한다.
 * issuerId는 이 연동이 속한 카드사로, 승인내역 카드 매칭 시 다른 카드사의 보유카드가 후보에
 * 섞이지 않도록 경계를 긋는 데 쓰인다. codefAccountCredentialId는 연동(linkId) 식별자로,
 * 보유카드 재조회 시 적재 대상 user_cards.codef_account_credential_id로 쓰인다.
 */
public record CodefConnection(
        String codefAccountCredentialId,
        String connectedId,
        String institutionCode,
        String issuerId,
        byte[] cardNumberEnc,
        byte[] cardPasswordEnc,
        byte[] birthDateEnc
) {
}
