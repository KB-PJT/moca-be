package com.moca.mocabe.domain.merchant.mapper;

import com.moca.mocabe.domain.merchant.model.MerchantCategoryRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MerchantCategoryMapper {

    /** 지도에 노출할 카테고리 전체를 display_order 기준으로 조회한다. */
    List<MerchantCategoryRow> findAllOrderedByDisplayOrder();
}
