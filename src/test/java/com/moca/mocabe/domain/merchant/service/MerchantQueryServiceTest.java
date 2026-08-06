package com.moca.mocabe.domain.merchant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.moca.mocabe.domain.merchant.dto.MerchantResponse;
import com.moca.mocabe.domain.merchant.mapper.MerchantMapper;
import com.moca.mocabe.domain.merchant.model.MerchantListRow;
import com.moca.mocabe.global.exception.merchant.InvalidMerchantQueryException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MerchantQueryServiceTest {

    private final MerchantMapper merchantMapper = mock(MerchantMapper.class);
    private final MerchantQueryService service = new MerchantQueryService(merchantMapper);

    @Test
    @DisplayName("categoryId로 조회한 활성 가맹점 목록을 응답으로 변환한다")
    void returnsMerchantsForCategory() {
        when(merchantMapper.findActiveMerchantsByCategoryId("cat-cafe")).thenReturn(List.of(
                new MerchantListRow("m-1", "스타벅스"),
                new MerchantListRow("m-2", "이디야")));

        List<MerchantResponse> merchants = service.getMerchantsByCategory("cat-cafe");

        assertEquals(2, merchants.size());
        assertEquals("스타벅스", merchants.get(0).name());
    }

    @Test
    @DisplayName("해당 카테고리에 가맹점이 없으면 빈 목록을 반환한다")
    void returnsEmptyListWhenNoMerchants() {
        when(merchantMapper.findActiveMerchantsByCategoryId("cat-empty")).thenReturn(List.of());

        assertTrue(service.getMerchantsByCategory("cat-empty").isEmpty());
    }

    @Test
    @DisplayName("categoryId가 비어 있으면 조회 없이 예외를 던진다")
    void rejectsBlankCategoryId() {
        assertThrows(InvalidMerchantQueryException.class, () -> service.getMerchantsByCategory("  "));

        verifyNoInteractions(merchantMapper);
    }
}
