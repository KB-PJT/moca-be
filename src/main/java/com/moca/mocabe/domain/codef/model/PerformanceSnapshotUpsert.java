package com.moca.mocabe.domain.codef.model;

/** user_card_performance_snapshots에 upsert할 카드 한 장·한 달치 실적 스냅샷이다. */
public record PerformanceSnapshotUpsert(
        String performanceSnapshotId,
        String userCardId,
        String performanceMonth,
        int currentSpendAmount
) {
}
