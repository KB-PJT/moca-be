package com.moca.mocabe.domain.merchant.service;

import com.moca.mocabe.domain.merchant.dto.MerchantCategoryResponse;
import com.moca.mocabe.domain.merchant.mapper.MerchantCategoryMapper;
import com.moca.mocabe.domain.merchant.model.MerchantCategoryRow;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/** 카테고리 목록 조회 유스케이스를 담당한다. */
public class MerchantCategoryQueryService {

    private final MerchantCategoryMapper merchantCategoryMapper;

    public MerchantCategoryQueryService(MerchantCategoryMapper merchantCategoryMapper) {
        this.merchantCategoryMapper = merchantCategoryMapper;
    }

    @Transactional(readOnly = true)
    public List<MerchantCategoryResponse> getCategories() {
        List<MerchantCategoryRow> rows = merchantCategoryMapper.findAllOrderedByDisplayOrder();
        return rows.stream()
                .map(row -> new MerchantCategoryResponse(
                        row.merchantCategoryId(), row.categoryCode(), row.categoryName()))
                .toList();
    }
}
