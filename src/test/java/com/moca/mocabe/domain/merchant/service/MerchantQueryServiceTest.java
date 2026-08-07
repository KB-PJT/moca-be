package com.moca.mocabe.domain.merchant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.moca.mocabe.domain.merchant.dto.MerchantResponse;
import com.moca.mocabe.domain.merchant.mapper.MerchantCategoryMapper;
import com.moca.mocabe.domain.merchant.mapper.MerchantMapper;
import com.moca.mocabe.domain.merchant.model.MerchantListRow;
import com.moca.mocabe.global.exception.merchant.InvalidMerchantQueryException;
import com.moca.mocabe.global.exception.merchant.MerchantCategoryNotFoundException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MerchantQueryServiceTest {

    private final MerchantMapper merchantMapper = mock(MerchantMapper.class);
    private final MerchantCategoryMapper merchantCategoryMapper = mock(MerchantCategoryMapper.class);
    private final MerchantQueryService service = new MerchantQueryService(merchantMapper, merchantCategoryMapper);

    @Test
    @DisplayName("categoryId로 조회한 활성 가맹점 목록을 응답으로 변환한다")
    void returnsMerchantsForCategory() {
        when(merchantCategoryMapper.existsMapVisibleCategory("cat-cafe")).thenReturn(true);
        when(merchantMapper.findActiveMerchantsByCategoryId("cat-cafe")).thenReturn(List.of(
                new MerchantListRow("m-1", "스타벅스"),
                new MerchantListRow("m-2", "이디야")));

        List<MerchantResponse> merchants = service.getMerchantsByCategory("cat-cafe");

        assertEquals(2, merchants.size());
        assertEquals("스타벅스", merchants.get(0).name());
    }

    @Test
    @DisplayName("카테고리는 있지만 가맹점이 없으면 빈 목록을 반환한다")
    void returnsEmptyListWhenNoMerchants() {
        when(merchantCategoryMapper.existsMapVisibleCategory("cat-empty")).thenReturn(true);
        when(merchantMapper.findActiveMerchantsByCategoryId("cat-empty")).thenReturn(List.of());

        assertTrue(service.getMerchantsByCategory("cat-empty").isEmpty());
    }

    @Test
    @DisplayName("categoryId가 비어 있으면 조회 없이 예외를 던진다")
    void rejectsBlankCategoryId() {
        assertThrows(InvalidMerchantQueryException.class, () -> service.getMerchantsByCategory("  "));

        verifyNoInteractions(merchantMapper, merchantCategoryMapper);
    }

    @Test
    @DisplayName("존재하지 않는 카테고리면 가맹점을 조회하지 않고 예외를 던진다")
    void rejectsUnknownCategory() {
        when(merchantCategoryMapper.existsMapVisibleCategory("cat-unknown")).thenReturn(false);

        assertThrows(MerchantCategoryNotFoundException.class,
                () -> service.getMerchantsByCategory("cat-unknown"));

        verify(merchantMapper, never()).findActiveMerchantsByCategoryId(anyString());
    }
}
