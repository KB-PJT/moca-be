package com.moca.mocabe.domain.card.mapper;

import com.moca.mocabe.domain.card.model.UserCardListRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 사용자 보유 카드 조회 영속성 접근을 담당한다. */
@Mapper
public interface UserCardMapper {

    List<UserCardListRow> findActiveByUserId(@Param("userId") String userId);

    List<UserCardListRow> findInactiveByUserId(@Param("userId") String userId);

    List<UserCardListRow> findHomeCardsByUserId(@Param("userId") String userId,
                                                @Param("orderMode") String orderMode);

    boolean existsByUserId(@Param("userId") String userId);

    UserCardListRow findByUserCardId(@Param("userCardId") String userCardId,
                                     @Param("userId") String userId);

    int updateMemo(@Param("userCardId") String userCardId,
                   @Param("userId") String userId,
                   @Param("memo") String memo);

    int deleteBenefitCalculationOutcomesByUserCardId(@Param("userCardId") String userCardId);

    int deleteBenefitUsagesByUserCardId(@Param("userCardId") String userCardId);

    int deleteOptionSelectionsByUserCardId(@Param("userCardId") String userCardId);

    int deletePerformanceSnapshotsByUserCardId(@Param("userCardId") String userCardId);

    int deletePaymentApprovalsByUserCardId(@Param("userCardId") String userCardId);

    int deleteUserCard(@Param("userCardId") String userCardId, @Param("userId") String userId);

    int deactivateUserCard(@Param("userCardId") String userCardId, @Param("userId") String userId);
}
