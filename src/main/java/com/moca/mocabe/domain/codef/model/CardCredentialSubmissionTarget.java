package com.moca.mocabe.domain.codef.model;

/**
 * PATCH /card-links/cards/{userCardId}/credentials(카드별 카드번호/비밀번호 추가 입력)에서 대상
 * 카드가 속한 연동·카드사 정책을 조회한 결과다. birthDateEnc는 CODEF 검증 호출 전 복호화해 쓴다.
 * cardId/cardNo/issuerName은 저장 성공 후 옵션 그룹을 포함한 카드 정보를 응답으로 돌려주는 데 쓰인다
 * (활성화는 이 응답을 보고 프론트가 별도로 PATCH /card-links/{linkId}/cards를 호출해야 한다).
 */
public record CardCredentialSubmissionTarget(
        String userCardId,
        String codefAccountCredentialId,
        String connectedId,
        String institutionCode,
        byte[] birthDateEnc,
        boolean requiresCardNo,
        boolean requiresCardPassword,
        String cardId,
        String cardNo,
        String issuerName
) {
}
