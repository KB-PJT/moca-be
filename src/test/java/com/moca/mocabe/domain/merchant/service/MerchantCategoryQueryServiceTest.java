package com.moca.mocabe.domain.merchant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moca.mocabe.domain.merchant.dto.MerchantCategoryResponse;
import com.moca.mocabe.domain.merchant.mapper.MerchantCategoryMapper;
import com.moca.mocabe.domain.merchant.model.MerchantCategoryRow;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MerchantCategoryQueryServiceTest {

    private final MerchantCategoryMapper merchantCategoryMapper = mock(MerchantCategoryMapper.class);
    private final MerchantCategoryQueryService service = new MerchantCategoryQueryService(merchantCategoryMapper);

    @Test
    @DisplayName("매퍼가 반환한 카테고리 행을 그대로 응답으로 변환한다")
    void mapsRowsToResponses() {
        when(merchantCategoryMapper.findAllOrderedByDisplayOrder()).thenReturn(List.of(
                new MerchantCategoryRow("cat-mart", "MART", "대형마트", 1),
                new MerchantCategoryRow("cat-cafe", "CAFE", "카페", 2)));

        List<MerchantCategoryResponse> categories = service.getCategories();

        assertEquals(2, categories.size());
        assertEquals("MART", categories.get(0).categoryCode());
        assertEquals("대형마트", categories.get(0).categoryName());
        assertEquals("CAFE", categories.get(1).categoryCode());
    }

    @Test
    @DisplayName("카테고리가 없으면 빈 목록을 반환한다")
    void returnsEmptyListWhenNoCategories() {
        when(merchantCategoryMapper.findAllOrderedByDisplayOrder()).thenReturn(List.of());

        assertTrue(service.getCategories().isEmpty());
    }
}
