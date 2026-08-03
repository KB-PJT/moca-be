package com.moca.mocabe.domain.codef.dto;

import java.util.List;

/** CODEF 연동 생성과 보유카드 후보 조회 결과다. */
public record CardLinkResponse(
        String linkId,
        String institutionCode,
        String status,
        List<CardLinkCardResponse> cards
) {
    public CardLinkResponse(String linkId, String institutionCode, String status) {
        this(linkId, institutionCode, status, List.of());
    }

    public String getLinkId() {
        return linkId;
    }

    public String getInstitutionCode() {
        return institutionCode;
    }

    public String getStatus() {
        return status;
    }
}
