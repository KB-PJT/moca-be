package com.moca.mocabe.domain.merchant.service;

import com.moca.mocabe.domain.merchant.mapper.MerchantMapper;

/**
 * 승인 가맹점명을 정규화한 뒤, 저장 가맹점명이 그 앞부분(접두사)과 일치하는 가맹점에 연결한다.
 *
 * merchants를 먼저 조회하고, 없으면 merchant_aliases에서 찾는다. 각 단계에서 접두사 후보가 여러 개면
 * 가장 긴(가장 구체적인) 이름을 채택한다. 일치하는 가맹점이 없으면 null을 반환한다.
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
        String byName = merchantMapper.findMerchantIdByNamePrefix(normalized);
        if (byName != null) {
            return byName;
        }
        return merchantMapper.findMerchantIdByAliasPrefix(normalized);
    }
}
