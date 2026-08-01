package com.moca.mocabe.domain.codef.service;

import com.moca.mocabe.domain.codef.exception.CodefAccountAlreadyLinkedException;
import com.moca.mocabe.domain.codef.mapper.CodefCredentialMapper;
import com.moca.mocabe.domain.codef.model.CodefAccountCredential;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

/** CODEF 자격정보 INSERT의 트랜잭션 경계를 담당한다. */
public class CodefCredentialStore {

    private final CodefCredentialMapper codefCredentialMapper;

    public CodefCredentialStore(CodefCredentialMapper codefCredentialMapper) {
        this.codefCredentialMapper = codefCredentialMapper;
    }

    @Transactional
    public void save(CodefAccountCredential credential) {
        try {
            codefCredentialMapper.insertAccountCredential(credential);
        } catch (DuplicateKeyException exception) {
            throw new CodefAccountAlreadyLinkedException();
        }
    }
}
