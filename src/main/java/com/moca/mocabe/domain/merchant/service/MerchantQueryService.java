package com.moca.mocabe.domain.merchant.service;

import com.moca.mocabe.domain.merchant.dto.MerchantResponse;
import com.moca.mocabe.domain.merchant.mapper.MerchantMapper;
import com.moca.mocabe.domain.merchant.model.MerchantListRow;
import com.moca.mocabe.global.exception.merchant.InvalidMerchantQueryException;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/** 카테고리별 가맹점 목록 조회 유스케이스를 담당한다. */
public class MerchantQueryService {

    private final MerchantMapper merchantMapper;

    public MerchantQueryService(MerchantMapper merchantMapper) {
        this.merchantMapper = merchantMapper;
    }

    @Transactional(readOnly = true)
    public List<MerchantResponse> getMerchantsByCategory(String categoryId) {
        if (categoryId == null || categoryId.isBlank()) {
            throw new InvalidMerchantQueryException("categoryId는 필수입니다.");
        }
        List<MerchantListRow> rows = merchantMapper.findActiveMerchantsByCategoryId(categoryId);
        return rows.stream()
                .map(row -> new MerchantResponse(row.merchantId(), row.name()))
                .toList();
    }
}
