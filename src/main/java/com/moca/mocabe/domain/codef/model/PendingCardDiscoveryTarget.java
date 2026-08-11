package com.moca.mocabe.domain.codef.model;

/**
 * POST /card-links/{linkId}/cards/discover(카드번호 필요 카드사의 2단계 보유카드 조회)에서
 * 대상 연동을 조회한 결과다. pendingCardNumberEnc/pendingCardPasswordEnc는 1단계
 * (POST /card-links)에서 입력받아 암호화해 잠깐 보관해둔 카드번호/비밀번호로, 조회 성공 후
 * CodefCredentialMapper.clearPendingCardCredentials로 지운다. requiresCardNo가 false인
 * 연동은 1단계에서 이미 보유카드 조회를 마쳤으므로 이 조회 대상이 아니다.
 */
public record PendingCardDiscoveryTarget(
        String codefAccountCredentialId,
        String connectedId,
        String institutionCode,
        byte[] birthDateEnc,
        boolean requiresCardNo,
        byte[] pendingCardNumberEnc,
        byte[] pendingCardPasswordEnc
) {
}
