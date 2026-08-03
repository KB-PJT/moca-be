package com.moca.mocabe.domain.codef.mapper;

import com.moca.mocabe.domain.codef.model.CodefAccountCredential;
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
}
