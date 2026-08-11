package com.moca.mocabe.domain.codef.mapper;

import com.moca.mocabe.domain.codef.model.CodefAccountCredential;
import com.moca.mocabe.domain.codef.model.CodefConnection;
import com.moca.mocabe.domain.codef.model.PendingCardDiscoveryTarget;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** CODEF 연동 자격정보 영속성 접근을 담당한다. */
@Mapper
public interface CodefCredentialMapper {

    boolean existsByUserIdAndIssuerIdAndIdentityHash(
            @Param("userId") String userId,
            @Param("issuerId") String issuerId,
            @Param("credentialIdentityHash") String credentialIdentityHash);

    void insertAccountCredential(CodefAccountCredential credential);

    String lockOwnedLink(@Param("linkId") String linkId,
                         @Param("userId") String userId);

    /** 승인내역 조회에 사용할 사용자의 활성 연동 목록을 카드사 기관코드와 함께 조회한다. */
    List<CodefConnection> findActiveConnectionsByUserId(@Param("userId") String userId);

    /**
     * POST /card-links/{linkId}/cards/discover 대상 연동을 조회한다(본인 소유 연동만). claim
     * 트랜잭션(짧은 SELECT ... FOR UPDATE) 안에서만 호출해, 동시 요청이 같은 pending 값으로
     * CODEF를 중복 호출하지 않도록 행을 잠근다.
     */
    PendingCardDiscoveryTarget findPendingDiscoveryTarget(
            @Param("linkId") String linkId, @Param("userId") String userId);

    /** claim 트랜잭션에서 pending 카드번호/비밀번호를 즉시 지운다. */
    void clearPendingCardCredentials(@Param("linkId") String linkId, @Param("userId") String userId);

    /** claim 이후 CODEF 호출·매칭이 실패했을 때, claim 시점에 읽어둔 값 그대로 pending을 되돌린다. */
    void restorePendingCardCredentials(@Param("linkId") String linkId, @Param("userId") String userId,
                                       @Param("pendingCardNumberEnc") byte[] pendingCardNumberEnc,
                                       @Param("pendingCardPasswordEnc") byte[] pendingCardPasswordEnc);
}
