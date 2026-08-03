package com.moca.mocabe.domain.codef.dto;

/** CODEF 연동 생성 결과다. 적재된 보유 카드 목록은 후속 작업에서 확장한다. */
public class CardLinkResponse {

    private final String linkId;
    private final String issuerId;
    private final String status;

    public CardLinkResponse(String linkId, String issuerId, String status) {
        this.linkId = linkId;
        this.issuerId = issuerId;
        this.status = status;
    }

    public String getLinkId() {
        return linkId;
    }

    public String getIssuerId() {
        return issuerId;
    }

    public String getStatus() {
        return status;
    }
}
