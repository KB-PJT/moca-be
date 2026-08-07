package com.moca.mocabe.domain.codef.model;

/**
 * 카드번호가 필요한 카드사에서 승인내역·실적조회를 카드별로 개별 호출하기 위한, 연동 하나에 속한
 * 활성 카드의 암호화된 카드번호/비밀번호다. cardNumberEnc/cardPasswordEnc는 호출 직전에 복호화해 쓴다.
 */
public record ActiveCardCredential(
        String userCardId,
        byte[] cardNumberEnc,
        byte[] cardPasswordEnc
) {
}
