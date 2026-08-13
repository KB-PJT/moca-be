package com.moca.mocabe.domain.home.mapper;

import com.moca.mocabe.domain.home.model.HomeCardRow;
import com.moca.mocabe.domain.home.model.RecentHistoryRow;
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

  /** 선택 옵션과 실적에 따라 제공된 지정 월 한도 중 사용하지 않은 원화 혜택을 합산한다. */
  Long sumMissedBenefitAmount(@Param("userId") String userId, @Param("yearMonth") String yearMonth);

  /** 지정한 서울 월 범위에 발생한 전체 결제 승인을 혜택 적용 여부와 함께 최신순으로 조회한다. */
  List<RecentHistoryRow> findRecentHistory(
      @Param("userId") String userId,
      @Param("fromUtc") LocalDateTime fromUtc,
      @Param("toUtc") LocalDateTime toUtc,
      @Param("limit") int limit);
}
