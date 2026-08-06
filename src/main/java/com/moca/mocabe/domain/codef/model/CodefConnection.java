package com.moca.mocabe.domain.codef.model;

/**
 * 승인내역 조회에 필요한 사용자별 CODEF 연동 정보다. birthDateEnc는 조회 시 복호화해 사용한다.
 * issuerId는 이 연동이 속한 카드사로, 승인내역 카드 매칭 시 다른 카드사의 보유카드가 후보에
 * 섞이지 않도록 경계를 긋는 데 쓰인다. codefAccountCredentialId는 연동(linkId) 식별자로,
 * 보유카드 재조회 시 적재 대상 user_cards.codef_account_credential_id로 쓰인다.
 * performanceLookbackMonths는 issuers.performance_lookback_months(카드사별 실적조회 가능 개월수)
 * 값을 그대로 담으며, null이면 정책이 확인되지 않은 카드사, -1이면 실적조회 자체를 지원하지 않는
 * 카드사다. issuerName은 -1인 경우 응답의 unsupportedPerformanceIssuers에 담을 표시용 이름이다.
 * cardNumberEnc/cardPasswordEnc는 KB 카드소지확인·현대카드 아이디로그인처럼 실적조회 시 카드번호·
 * 카드비밀번호 인증이 필요한 카드사를 위한 값으로, 연동 시점에 저장되지 않은 카드사는 null이다.
 */
public record CodefConnection(
        String codefAccountCredentialId,
        String connectedId,
        String institutionCode,
        String issuerId,
        String issuerName,
        Integer performanceLookbackMonths,
        byte[] birthDateEnc,
        byte[] cardNumberEnc,
        byte[] cardPasswordEnc
) {
}
