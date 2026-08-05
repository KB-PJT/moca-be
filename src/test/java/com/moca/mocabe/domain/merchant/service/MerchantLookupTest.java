package com.moca.mocabe.domain.merchant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.moca.mocabe.domain.merchant.mapper.MerchantMapper;
import com.moca.mocabe.domain.merchant.model.MerchantNameCandidate;
import java.util.List;
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
    @DisplayName("가맹점명이 승인명의 접두사이면 매칭한다")
    void resolvesByPrefixMatch() {
        when(merchantMapper.findActiveMerchantNameCandidates()).thenReturn(
                List.of(new MerchantNameCandidate("m-1", "스타벅스")));

        assertEquals("m-1", merchantLookup.resolveMerchantId("스타벅스 강남점"));
    }

    @Test
    @DisplayName("접두사 후보가 여러 개면 가장 긴(구체적인) 것을 채택한다")
    void resolvesLongestPrefixMatch() {
        when(merchantMapper.findActiveMerchantNameCandidates()).thenReturn(List.of(
                new MerchantNameCandidate("m-1", "메가"),
                new MerchantNameCandidate("m-2", "메가커피")));

        assertEquals("m-2", merchantLookup.resolveMerchantId("메가커피 어린이대공원역점"));
    }

    @Test
    @DisplayName("접두사가 아니라 꼬리에 들어있는 가맹점명은 매칭하지 않는다")
    void doesNotMatchWhenNameIsInTail() {
        // 승인명 "메가커피어린이대공원역점"의 꼬리에 '어린이대공원'이 있어도 접두사가 아니므로 탈락한다.
        when(merchantMapper.findActiveMerchantNameCandidates()).thenReturn(
                List.of(new MerchantNameCandidate("m-1", "어린이대공원")));

        assertNull(merchantLookup.resolveMerchantId("메가커피어린이대공원역점"));
    }

    @Test
    @DisplayName("2글자 짧은 브랜드도 접두사로 매칭된다")
    void matchesShortBrandPrefix() {
        when(merchantMapper.findActiveMerchantNameCandidates()).thenReturn(
                List.of(new MerchantNameCandidate("m-1", "CU")));

        assertEquals("m-1", merchantLookup.resolveMerchantId("CU강남역점"));
    }

    @Test
    @DisplayName("merchants에서 접두사가 매칭되면 alias는 조회하지 않고 그 merchant_id를 반환한다")
    void resolvesFromMerchantsFirst() {
        when(merchantMapper.findActiveMerchantNameCandidates()).thenReturn(
                List.of(new MerchantNameCandidate("m-1", "메가커피")));

        assertEquals("m-1", merchantLookup.resolveMerchantId("메가커피 어린이대공원역점"));
        verify(merchantMapper).findActiveMerchantNameCandidates();
        org.mockito.Mockito.verify(merchantMapper, org.mockito.Mockito.never())
                .findActiveMerchantAliasCandidates();
    }

    @Test
    @DisplayName("merchants에서 못 찾으면 merchant_aliases에서 접두사로 찾는다")
    void fallsBackToAliases() {
        when(merchantMapper.findActiveMerchantNameCandidates()).thenReturn(List.of());
        when(merchantMapper.findActiveMerchantAliasCandidates()).thenReturn(
                List.of(new MerchantNameCandidate("m-2", "메가엠지씨MGC커피")));

        assertEquals("m-2", merchantLookup.resolveMerchantId("메가엠지씨MGC커피"));
    }

    @Test
    @DisplayName("별칭도 가장 긴(구체적인) 접두사를 채택한다")
    void resolvesLongestAliasPrefixMatch() {
        when(merchantMapper.findActiveMerchantNameCandidates()).thenReturn(List.of());
        when(merchantMapper.findActiveMerchantAliasCandidates()).thenReturn(List.of(
                new MerchantNameCandidate("m-generic", "메가"),
                new MerchantNameCandidate("m-specific", "메가엠지씨MGC커피")));

        assertEquals("m-specific", merchantLookup.resolveMerchantId("메가엠지씨MGC커피역삼점"));
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
        when(merchantMapper.findActiveMerchantNameCandidates()).thenReturn(
                List.of(new MerchantNameCandidate("m-1", "스타벅스")));
        when(merchantMapper.findActiveMerchantAliasCandidates()).thenReturn(List.of());

        assertNull(merchantLookup.resolveMerchantId("미등록가맹점"));
    }
}
