package com.moca.mocabe.domain.report.mapper;

import com.moca.mocabe.domain.report.model.BenefitTypeAmountRow;
import com.moca.mocabe.domain.report.model.CategoryBenefitRow;
import com.moca.mocabe.domain.report.model.MissedBenefitRow;
import com.moca.mocabe.domain.report.model.PerformanceCardRow;
import com.moca.mocabe.domain.report.model.PerformanceTierRow;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 혜택·실적 리포트 화면 전용 읽기 모델을 조회한다. */
@Mapper
public interface ReportMapper {

  List<BenefitTypeAmountRow> findBenefitAmountsByType(
      @Param("userId") String userId,
      @Param("fromUtc") LocalDateTime fromUtc,
      @Param("toUtc") LocalDateTime toUtc);

  List<CategoryBenefitRow> findBenefitAmountsByCategory(
      @Param("userId") String userId,
      @Param("fromUtc") LocalDateTime fromUtc,
      @Param("toUtc") LocalDateTime toUtc,
      @Param("limit") int limit);

  List<MissedBenefitRow> findMonthlyRemainingBenefits(
      @Param("userId") String userId,
      @Param("userCardId") String userCardId,
      @Param("yearMonth") String yearMonth);

  PerformanceCardRow findPerformanceCard(
      @Param("userId") String userId,
      @Param("userCardId") String userCardId,
      @Param("yearMonth") String yearMonth);

  List<PerformanceCardRow> findPerformanceCards(
      @Param("userId") String userId, @Param("yearMonth") String yearMonth);

  List<PerformanceTierRow> findPerformanceTiers(@Param("userId") String userId);
}
