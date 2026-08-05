package com.moca.mocabe.domain.merchant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.moca.mocabe.domain.merchant.mapper.MerchantMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MerchantLookupTest {

    private MerchantMapper merchantMapper;
    private MerchantLookup merchantLookup;

    @BeforeEach
    void setUp() {
        merchantMapper = org.mockito.Mockito.mock(MerchantMapper.class);
        merchantLookup = new MerchantLookup(merchantMapper, new MerchantNameNormalizer());
    }

    @Test
    @DisplayName("merchants에서 접두사가 매칭되면 alias는 조회하지 않고 그 merchant_id를 반환한다")
    void resolvesFromMerchantsFirst() {
        when(merchantMapper.findMerchantIdByNamePrefix("메가커피어린이대공원역점")).thenReturn("m-1");

        assertEquals("m-1", merchantLookup.resolveMerchantId("메가커피 어린이대공원역점"));
        verify(merchantMapper).findMerchantIdByNamePrefix("메가커피어린이대공원역점");
        verifyNoMoreInteractions(merchantMapper);
    }

    @Test
    @DisplayName("merchants에서 못 찾으면 merchant_aliases에서 접두사로 찾는다")
    void fallsBackToAliases() {
        when(merchantMapper.findMerchantIdByNamePrefix("메가엠지씨MGC커피")).thenReturn(null);
        when(merchantMapper.findMerchantIdByAliasPrefix("메가엠지씨MGC커피")).thenReturn("m-2");

        assertEquals("m-2", merchantLookup.resolveMerchantId("메가엠지씨MGC커피"));
    }

    @Test
    @DisplayName("이름이 비면 조회하지 않고 null을 반환한다")
    void returnsNullForBlankName() {
        assertNull(merchantLookup.resolveMerchantId("  "));
        verifyNoInteractions(merchantMapper);
    }

    @Test
    @DisplayName("merchants·aliases 모두 일치하지 않으면 null을 반환한다")
    void returnsNullWhenNoMatch() {
        when(merchantMapper.findMerchantIdByNamePrefix("미등록가맹점")).thenReturn(null);
        when(merchantMapper.findMerchantIdByAliasPrefix("미등록가맹점")).thenReturn(null);

        assertNull(merchantLookup.resolveMerchantId("미등록가맹점"));
    }
}
