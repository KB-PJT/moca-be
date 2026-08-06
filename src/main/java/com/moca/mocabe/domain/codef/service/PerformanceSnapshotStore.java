package com.moca.mocabe.domain.codef.service;

import com.moca.mocabe.domain.codef.mapper.CardPerformanceMapper;
import com.moca.mocabe.domain.codef.model.PerformanceSnapshotUpsert;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/** 카드 실적 스냅샷 upsert의 트랜잭션 경계를 담당한다. */
public class PerformanceSnapshotStore {

    private final CardPerformanceMapper cardPerformanceMapper;

    public PerformanceSnapshotStore(CardPerformanceMapper cardPerformanceMapper) {
        this.cardPerformanceMapper = cardPerformanceMapper;
    }

    /** 카드·달마다 실적 스냅샷을 upsert하고 처리 건수를 반환한다. */
    @Transactional
    public int upsertAll(List<PerformanceSnapshotUpsert> snapshots) {
        for (PerformanceSnapshotUpsert snapshot : snapshots) {
            cardPerformanceMapper.upsertPerformanceSnapshot(snapshot.performanceSnapshotId(),
                    snapshot.userCardId(), snapshot.performanceMonth(), snapshot.currentSpendAmount());
        }
        return snapshots.size();
    }
}
