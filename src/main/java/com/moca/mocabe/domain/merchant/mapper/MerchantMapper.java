package com.moca.mocabe.domain.merchant.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 가맹점 조회 영속성 접근을 담당한다. */
@Mapper
public interface MerchantMapper {

    /**
     * 정규화된 저장 가맹점명이 정규화된 승인 가맹점명의 접두사인 활성 가맹점 중,
     * 가장 긴(=가장 구체적인) 것의 merchant_id를 반환한다. 없으면 null.
     */
    String findMerchantIdByNamePrefix(@Param("normalizedApprovalName") String normalizedApprovalName);

    /**
     * merchants에서 접두사 매칭이 없을 때 사용한다. 정규화된 별칭이 승인 가맹점명의 접두사인
     * 활성 가맹점의 별칭 중 가장 긴 것의 merchant_id를 반환한다. 없으면 null.
     */
    String findMerchantIdByAliasPrefix(@Param("normalizedApprovalName") String normalizedApprovalName);
}
