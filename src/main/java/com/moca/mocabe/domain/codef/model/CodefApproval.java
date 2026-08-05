package com.moca.mocabe.domain.codef.model;

/**
 * CODEF 승인내역(approval-list) 조회 응답 한 건의 내부 모델이다.
 *
 * 카드번호(cardNo)는 응답 DTO나 로그로 내보내지 않는다. sourcePayload는 CODEF 원본 JSON을 그대로 보존해
 * card_payment_approvals.source_payload에 적재하기 위한 값이다.
 */
public record CodefApproval(
        String usedDate,
        String usedTime,
        String cardNo,
        String cardName,
        String memberStoreName,
        String usedAmount,
        String approvalNo,
        String homeForeignType,
        String cancelYN,
        String sourcePayload
) {

    /** 취소/부분취소/거절이 아닌 정상 승인건인지 여부. */
    public boolean isNormalApproval() {
        return "0".equals(cancelYN);
    }

    /** 국내 결제건인지 여부(해외결제는 적재 대상이 아니다). */
    public boolean isDomestic() {
        return "1".equals(homeForeignType);
    }
}
