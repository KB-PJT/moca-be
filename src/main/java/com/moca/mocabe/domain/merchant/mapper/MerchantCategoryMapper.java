package com.moca.mocabe.domain.merchant.mapper;

import com.moca.mocabe.domain.merchant.model.MerchantCategoryRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MerchantCategoryMapper {

    /** 지도에 노출할 카테고리 전체를 display_order 기준으로 조회한다. */
    List<MerchantCategoryRow> findAllOrderedByDisplayOrder();

    /** 카테고리에 활성 매핑된 카카오 카테고리 그룹코드를 priority 순으로 조회한다. 없으면 빈 목록(키워드 검색 대상). */
    List<String> findEnabledKakaoGroupCodes(@Param("merchantCategoryId") String merchantCategoryId);

    /** 지도 대상 카테고리로 존재하는지 확인한다. */
    boolean existsMapVisibleCategory(@Param("merchantCategoryId") String merchantCategoryId);
}
