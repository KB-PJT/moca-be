package com.moca.mocabe.domain.benefit.model;

/**
 * CODEF 승인내역 한 건에 대해 우리 카드 혜택 룰로 역산한 결과다.
 */
public record BenefitInferenceResult(
        CodefApprovalRecord approval,
        BenefitCalculationResult calculationResult
) {
}
