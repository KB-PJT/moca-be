package com.moca.mocabe.domain.codef.mapper;

import com.moca.mocabe.domain.codef.model.CodefAccountCredential;
import com.moca.mocabe.domain.codef.model.CodefConnection;
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
}
