package com.moca.mocabe.domain.benefit.mapper;

import com.moca.mocabe.domain.benefit.model.BenefitHistoryDetailRow;
import com.moca.mocabe.domain.benefit.model.BenefitHistoryRow;
import com.moca.mocabe.domain.benefit.model.BenefitHistorySummaryRow;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 사용자의 전체 카드 결제 내역과 혜택 계산 결과 조회를 담당한다. */
@Mapper
public interface BenefitHistoryMapper {

  List<BenefitHistoryRow> findHistory(
      @Param("userId") String userId,
      @Param("fromUtc") LocalDateTime fromUtc,
      @Param("toUtc") LocalDateTime toUtc,
      @Param("userCardId") String userCardId,
      @Param("benefitType") String benefitType);

  BenefitHistorySummaryRow summarizeHistory(
      @Param("userId") String userId,
      @Param("fromUtc") LocalDateTime fromUtc,
      @Param("toUtc") LocalDateTime toUtc,
      @Param("userCardId") String userCardId);

  BenefitHistoryDetailRow findDetail(
      @Param("userId") String userId, @Param("usageId") String usageId);
}
