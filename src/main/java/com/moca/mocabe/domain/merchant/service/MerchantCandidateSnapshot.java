package com.moca.mocabe.domain.merchant.service;

import com.moca.mocabe.domain.merchant.model.MerchantNameCandidate;
import java.util.List;

/**
 * 가맹점 접두사 매칭 후보(정식명·별칭)를 한 번만 조회해 둔 스냅샷이다.
 *
 * 승인건마다 DB를 다시 조회하지 않도록, {@link MerchantLookup#loadCandidates()}로 동기화 한 회차 시작
 * 시점에 한 번만 만들어 그 안에서는 이 스냅샷으로만 매칭한다. 접두사 판정·최장일치·merchants 우선/
 * alias 폴백 규칙은 여기서 수행한다.
 */
public class MerchantCandidateSnapshot {

    private final List<MerchantNameCandidate> nameCandidates;
    private final List<MerchantNameCandidate> aliasCandidates;
    private final MerchantNameNormalizer merchantNameNormalizer;

    MerchantCandidateSnapshot(List<MerchantNameCandidate> nameCandidates,
                              List<MerchantNameCandidate> aliasCandidates,
                              MerchantNameNormalizer merchantNameNormalizer) {
        this.nameCandidates = nameCandidates;
        this.aliasCandidates = aliasCandidates;
        this.merchantNameNormalizer = merchantNameNormalizer;
    }

    /** 매칭되는 merchant_id를 반환한다. 이름이 비었거나 일치하는 가맹점이 없으면 null. */
    public String resolveMerchantId(String rawMerchantName) {
        String normalized = merchantNameNormalizer.normalize(rawMerchantName);
        if (normalized.isEmpty()) {
            return null;
        }
        String byName = longestPrefixMatch(nameCandidates, normalized);
        if (byName != null) {
            return byName;
        }
        return longestPrefixMatch(aliasCandidates, normalized);
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
