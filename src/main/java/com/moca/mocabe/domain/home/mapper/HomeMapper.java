package com.moca.mocabe.domain.home.mapper;

import com.moca.mocabe.domain.home.model.HomeCardRow;
import com.moca.mocabe.domain.home.model.RecentBenefitRow;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 홈 화면에 필요한 카드·혜택 집계 조회를 담당한다. */
@Mapper
public interface HomeMapper {

  /** 사용자의 활성 카드별 지정 월 혜택 한도·수령액·실적을 조회한다. */
  List<HomeCardRow> findHomeCards(
      @Param("userId") String userId, @Param("yearMonth") String yearMonth);

  /** 계산 결과 원장 기준으로 지정 월의 실제 미적용 원화 혜택을 합산한다. */
  Long sumMissedBenefitAmount(@Param("userId") String userId, @Param("yearMonth") String yearMonth);

  /** 지정한 서울 월 범위에 발생한 최근 확정 혜택을 최신순으로 조회한다. */
  List<RecentBenefitRow> findRecentBenefits(
      @Param("userId") String userId,
      @Param("fromUtc") LocalDateTime fromUtc,
      @Param("toUtc") LocalDateTime toUtc,
      @Param("limit") int limit);
}
