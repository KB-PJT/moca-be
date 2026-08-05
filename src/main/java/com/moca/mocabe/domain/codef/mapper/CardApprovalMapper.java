package com.moca.mocabe.domain.codef.mapper;

import com.moca.mocabe.domain.codef.model.ExistingApprovalKey;
import com.moca.mocabe.domain.codef.model.UserCardMatchRow;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 카드 결제 승인내역 적재와 중복 판정 영속성 접근을 담당한다. */
@Mapper
public interface CardApprovalMapper {

    /** 승인내역 카드 매칭에 사용할 사용자의 보유카드(카드명·마스킹 카드번호)를 조회한다. */
    List<UserCardMatchRow> findUserCardsForMatching(@Param("userId") String userId);

    /** 지정 기간(UTC) 안에 이미 적재된 승인내역의 중복 판정 키를 조회한다. */
    List<ExistingApprovalKey> findExistingApprovalKeys(@Param("userId") String userId,
                                                       @Param("fromUtc") LocalDateTime fromUtc,
                                                       @Param("toUtc") LocalDateTime toUtc);

    // MyBatis 기본 Reflector는 record 접근자(approvalId())를 getter로 인식하지 못하므로
    // record 객체를 통째로 넘기지 않고 @Param 개별 인자로 전달한다(insertLinkedCard와 동일 패턴).
    void insertApproval(@Param("approvalId") String approvalId,
                        @Param("userId") String userId,
                        @Param("userCardId") String userCardId,
                        @Param("merchantId") String merchantId,
                        @Param("approvalNumber") String approvalNumber,
                        @Param("approvedAt") LocalDateTime approvedAt,
                        @Param("merchantName") String merchantName,
                        @Param("amount") int amount,
                        @Param("sourcePayload") String sourcePayload);
}
