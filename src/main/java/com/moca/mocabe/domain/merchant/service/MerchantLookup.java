package com.moca.mocabe.domain.merchant.service;

import com.moca.mocabe.domain.merchant.mapper.MerchantMapper;

/**
 * 가맹점 접두사 매칭 후보를 조회해 {@link MerchantCandidateSnapshot}을 만든다.
 *
 * 승인건마다 후보를 다시 조회하면 DB 조회·접두사 비교 비용이 승인건 수만큼 반복되므로, 호출자는
 * 동기화 한 회차 시작 시점에 {@link #loadCandidates()}를 한 번만 호출해 스냅샷을 만들고, 그 회차 동안
 * 발생하는 모든 매칭은 그 스냅샷으로만 수행해야 한다.
 */
public class MerchantLookup {

    private final MerchantMapper merchantMapper;
    private final MerchantNameNormalizer merchantNameNormalizer;

    public MerchantLookup(MerchantMapper merchantMapper, MerchantNameNormalizer merchantNameNormalizer) {
        this.merchantMapper = merchantMapper;
        this.merchantNameNormalizer = merchantNameNormalizer;
    }

    /** 활성 가맹점·별칭 후보를 한 번 조회해 스냅샷으로 만든다. */
    public MerchantCandidateSnapshot loadCandidates() {
        return new MerchantCandidateSnapshot(
                merchantMapper.findActiveMerchantNameCandidates(),
                merchantMapper.findActiveMerchantAliasCandidates(),
                merchantNameNormalizer);
    }
}
