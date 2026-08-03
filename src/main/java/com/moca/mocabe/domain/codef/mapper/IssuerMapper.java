package com.moca.mocabe.domain.codef.mapper;

import com.moca.mocabe.domain.codef.model.CodefIssuerPolicy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 발급사(issuers) 기준정보 조회를 담당한다. */
@Mapper
public interface IssuerMapper {

    /** issuer_id로 CODEF 기관코드와 카드사별 필수정보 정책을 조회한다. 없으면 null. */
    CodefIssuerPolicy findCodefPolicyByIssuerId(@Param("issuerId") String issuerId);
}
