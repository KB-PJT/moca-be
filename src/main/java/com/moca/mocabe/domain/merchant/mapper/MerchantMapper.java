package com.moca.mocabe.domain.merchant.mapper;

import com.moca.mocabe.domain.merchant.model.MerchantNameCandidate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * 가맹점 후보 데이터 조회만 담당한다. 접두사 판정·최장일치·동률 해소 같은 매칭 규칙은
 * 도메인 계층({@link com.moca.mocabe.domain.merchant.service.MerchantLookup})에서 수행한다.
 */
@Mapper
public interface MerchantMapper {

    /** 활성 가맹점의 (merchant_id, normalized_name) 후보 목록을 조회한다. */
    List<MerchantNameCandidate> findActiveMerchantNameCandidates();

    /** 활성 가맹점에 속한 별칭의 (merchant_id, normalized_alias_name) 후보 목록을 조회한다. */
    List<MerchantNameCandidate> findActiveMerchantAliasCandidates();
}
