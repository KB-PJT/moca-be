package com.moca.mocabe.domain.merchant.service;

import com.moca.mocabe.domain.merchant.mapper.MerchantMapper;
import com.moca.mocabe.domain.merchant.model.MerchantNameCandidate;
import java.util.List;

/**
 * 승인 가맹점명을 정규화한 뒤, 저장 가맹점명이 그 앞부분(접두사)과 일치하는 가맹점에 연결한다.
 *
 * merchants를 먼저 조회하고, 없으면 merchant_aliases에서 찾는다. 각 단계에서 접두사 후보가 여러 개면
 * 가장 긴(가장 구체적인) 이름을 채택한다(동률이면 먼저 조회된 후보를 유지). 일치하는 가맹점이 없으면
 * null을 반환한다. 접두사 판정·최장일치·동률 해소는 모두 여기(도메인 계층)에서 수행하고, Mapper는
 * 후보 데이터 조회만 담당한다.
 */
public class MerchantLookup {

    private final MerchantMapper merchantMapper;
    private final MerchantNameNormalizer merchantNameNormalizer;

    public MerchantLookup(MerchantMapper merchantMapper, MerchantNameNormalizer merchantNameNormalizer) {
        this.merchantMapper = merchantMapper;
        this.merchantNameNormalizer = merchantNameNormalizer;
    }

    /** 매칭되는 merchant_id를 반환한다. 이름이 비었거나 일치하는 가맹점이 없으면 null. */
    public String resolveMerchantId(String rawMerchantName) {
        String normalized = merchantNameNormalizer.normalize(rawMerchantName);
        if (normalized.isEmpty()) {
            return null;
        }
        String byName = longestPrefixMatch(merchantMapper.findActiveMerchantNameCandidates(), normalized);
        if (byName != null) {
            return byName;
        }
        return longestPrefixMatch(merchantMapper.findActiveMerchantAliasCandidates(), normalized);
    }

    private String longestPrefixMatch(List<MerchantNameCandidate> candidates, String normalizedApprovalName) {
        String bestMerchantId = null;
        int bestLength = 0;
        for (MerchantNameCandidate candidate : candidates) {
            String candidateName = candidate.normalizedName();
            if (candidateName != null && !candidateName.isEmpty()
                    && normalizedApprovalName.startsWith(candidateName)
                    && candidateName.length() > bestLength) {
                bestLength = candidateName.length();
                bestMerchantId = candidate.merchantId();
            }
        }
        return bestMerchantId;
    }
}
