package com.moca.mocabe.domain.codef.model;

import java.time.LocalDateTime;

/** card_payment_approvals에 적재할 승인내역 한 건이다. approvedAt은 UTC 기준 시각이다. */
public record ApprovalInsert(
        String approvalId,
        String userId,
        String userCardId,
        String merchantId,
        String approvalNumber,
        LocalDateTime approvedAt,
        String merchantName,
        int amount,
        String sourcePayload
) {
}
