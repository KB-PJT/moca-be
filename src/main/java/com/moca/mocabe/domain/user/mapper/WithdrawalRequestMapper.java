package com.moca.mocabe.domain.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 탈퇴 사유 통계 영속성 접근을 담당한다. */
@Mapper
public interface WithdrawalRequestMapper {

    int insertWithdrawalRequest(@Param("reasonCode") String reasonCode,
                                @Param("reasonText") String reasonText,
                                @Param("confirmed") boolean confirmed);
}
