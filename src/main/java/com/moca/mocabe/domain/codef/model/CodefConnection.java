package com.moca.mocabe.domain.codef.model;

/** 승인내역 조회에 필요한 사용자별 CODEF 연동 정보다. birthDateEnc는 조회 시 복호화해 사용한다. */
public record CodefConnection(
        String connectedId,
        String institutionCode,
        byte[] birthDateEnc
) {
}
