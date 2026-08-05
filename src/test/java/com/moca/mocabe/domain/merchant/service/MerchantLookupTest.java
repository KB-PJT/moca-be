package com.moca.mocabe.domain.merchant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    @DisplayName("후보를 조회해 매칭 가능한 스냅샷을 만든다")
    void loadsCandidatesIntoWorkingSnapshot() {
        when(merchantMapper.findActiveMerchantNameCandidates()).thenReturn(
                List.of(new MerchantNameCandidate("m-1", "스타벅스")));
        when(merchantMapper.findActiveMerchantAliasCandidates()).thenReturn(List.of());

        MerchantCandidateSnapshot snapshot = merchantLookup.loadCandidates();

        assertEquals("m-1", snapshot.resolveMerchantId("스타벅스 강남점"));
    }

    @Test
    @DisplayName("일치하는 후보가 없는 스냅샷은 null을 반환한다")
    void loadsEmptySnapshotWhenNoCandidates() {
        when(merchantMapper.findActiveMerchantNameCandidates()).thenReturn(List.of());
        when(merchantMapper.findActiveMerchantAliasCandidates()).thenReturn(List.of());

        MerchantCandidateSnapshot snapshot = merchantLookup.loadCandidates();

        assertNull(snapshot.resolveMerchantId("스타벅스"));
    }
}
