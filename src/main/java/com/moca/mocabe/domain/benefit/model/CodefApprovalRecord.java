package com.moca.mocabe.domain.benefit.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CODEF 승인내역 응답을 혜택 계산에 필요한 값만 남겨 정규화한 모델이다.
 */
public record CodefApprovalRecord(
        String approvalId,
        BigDecimal paymentAmount,
        BigDecimal usageQuantity,
        LocalDateTime approvedAt,
        LocalDateTime capturedAt,
        String mocaCategory,
        boolean merchantEligible,
        boolean paymentChannelEligible
) {
}
