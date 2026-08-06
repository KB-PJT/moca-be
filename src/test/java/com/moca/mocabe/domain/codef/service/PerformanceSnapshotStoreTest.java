package com.moca.mocabe.domain.codef.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.moca.mocabe.domain.codef.mapper.CardPerformanceMapper;
import com.moca.mocabe.domain.codef.model.PerformanceSnapshotUpsert;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PerformanceSnapshotStoreTest {

    private CardPerformanceMapper cardPerformanceMapper;
    private PerformanceSnapshotStore store;

    @BeforeEach
    void setUp() {
        cardPerformanceMapper = mock(CardPerformanceMapper.class);
        store = new PerformanceSnapshotStore(cardPerformanceMapper);
    }

    @Test
    @DisplayName("모든 실적 스냅샷을 upsert하고 처리 건수를 반환한다")
    void upsertsAll() {
        doNothing().when(cardPerformanceMapper)
                .upsertPerformanceSnapshot(anyString(), anyString(), anyString(), anyInt());

        int upserted = store.upsertAll(List.of(snapshot("p-1"), snapshot("p-2")));

        assertEquals(2, upserted);
        verify(cardPerformanceMapper, times(2))
                .upsertPerformanceSnapshot(anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("빈 목록이면 아무것도 upsert하지 않고 0을 반환한다")
    void upsertsNothingWhenEmpty() {
        int upserted = store.upsertAll(List.of());

        assertEquals(0, upserted);
        verify(cardPerformanceMapper, times(0))
                .upsertPerformanceSnapshot(anyString(), anyString(), anyString(), anyInt());
    }

    private PerformanceSnapshotUpsert snapshot(String id) {
        return new PerformanceSnapshotUpsert(id, "uc-1", "2026-08", 300000);
    }
}
