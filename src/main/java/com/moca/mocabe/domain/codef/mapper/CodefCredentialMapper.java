package com.moca.mocabe.domain.codef.mapper;

import com.moca.mocabe.domain.codef.model.CodefAccountCredential;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** CODEF 연동 자격정보 영속성 접근을 담당한다. */
@Mapper
public interface CodefCredentialMapper {

    boolean existsByUserIdAndIssuerIdAndFingerprint(
            @Param("userId") String userId,
            @Param("issuerId") String issuerId,
            @Param("credentialFingerprint") String credentialFingerprint);

    void insertAccountCredential(CodefAccountCredential credential);
}
