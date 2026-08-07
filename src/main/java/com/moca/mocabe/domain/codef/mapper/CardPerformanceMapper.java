package com.moca.mocabe.domain.codef.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 카드 실적 스냅샷(user_card_performance_snapshots) 영속성 접근을 담당한다. */
@Mapper
public interface CardPerformanceMapper {

    /** (user_card_id, performance_month)가 이미 있으면 current_spend_amount를 최신 값으로 덮어쓴다. */
    void upsertPerformanceSnapshot(@Param("performanceSnapshotId") String performanceSnapshotId,
                                   @Param("userCardId") String userCardId,
                                   @Param("performanceMonth") String performanceMonth,
                                   @Param("currentSpendAmount") int currentSpendAmount);
}
