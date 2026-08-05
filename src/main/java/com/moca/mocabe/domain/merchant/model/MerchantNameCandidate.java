package com.moca.mocabe.domain.merchant.model;

/** 가맹점 접두사 매칭 후보 한 건이다(정식명 또는 별칭 공용). normalizedName은 DB에 저장된 정규화 값이다. */
public record MerchantNameCandidate(
        String merchantId,
        String normalizedName
) {
}
