package com.moca.mocabe.domain.merchant.service;

import com.moca.mocabe.domain.merchant.dto.MerchantResponse;
import com.moca.mocabe.domain.merchant.mapper.MerchantCategoryMapper;
import com.moca.mocabe.domain.merchant.mapper.MerchantMapper;
import com.moca.mocabe.domain.merchant.model.MerchantListRow;
import com.moca.mocabe.global.exception.merchant.InvalidMerchantQueryException;
import com.moca.mocabe.global.exception.merchant.MerchantCategoryNotFoundException;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/** 카테고리별 가맹점 목록 조회 유스케이스를 담당한다. */
public class MerchantQueryService {

    private final MerchantMapper merchantMapper;
    private final MerchantCategoryMapper merchantCategoryMapper;

    public MerchantQueryService(MerchantMapper merchantMapper, MerchantCategoryMapper merchantCategoryMapper) {
        this.merchantMapper = merchantMapper;
        this.merchantCategoryMapper = merchantCategoryMapper;
    }

    @Transactional(readOnly = true)
    public List<MerchantResponse> getMerchantsByCategory(String categoryId) {
        if (categoryId == null || categoryId.isBlank()) {
            throw new InvalidMerchantQueryException("categoryId는 필수입니다.");
        }
        if (!merchantCategoryMapper.existsMapVisibleCategory(categoryId)) {
            throw new MerchantCategoryNotFoundException("존재하지 않는 카테고리입니다. categoryId=" + categoryId);
        }
        List<MerchantListRow> rows = merchantMapper.findActiveMerchantsByCategoryId(categoryId);
        return rows.stream()
                .map(row -> new MerchantResponse(row.merchantId(), row.name()))
                .toList();
    }
}
