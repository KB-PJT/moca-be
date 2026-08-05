package com.moca.mocabe.domain.codef.model;

import java.time.LocalDateTime;

/** 이미 적재된 승인내역의 중복 판정용 자연키다. approvalNumber가 없으면 시각·금액·가맹점명으로 판정한다. */
public record ExistingApprovalKey(
        String userCardId,
        String approvalNumber,
        LocalDateTime approvedAt,
        int amount,
        String merchantName
) {
}
