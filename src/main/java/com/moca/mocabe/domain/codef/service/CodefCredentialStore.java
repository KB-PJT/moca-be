package com.moca.mocabe.domain.codef.service;

import com.moca.mocabe.domain.codef.exception.CodefAccountAlreadyLinkedException;
import com.moca.mocabe.domain.codef.mapper.CodefCredentialMapper;
import com.moca.mocabe.domain.codef.mapper.LinkedCardMapper;
import com.moca.mocabe.domain.codef.model.CodefAccountCredential;
import com.moca.mocabe.domain.codef.model.LinkedCardInsert;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

/** CODEF 자격정보와 매칭된 보유카드 적재의 트랜잭션 경계를 담당한다. */
public class CodefCredentialStore {

    private final CodefCredentialMapper codefCredentialMapper;
    private final LinkedCardMapper linkedCardMapper;

    public CodefCredentialStore(CodefCredentialMapper codefCredentialMapper,
                                LinkedCardMapper linkedCardMapper) {
        this.codefCredentialMapper = codefCredentialMapper;
        this.linkedCardMapper = linkedCardMapper;
    }

    @Transactional
    public void save(CodefAccountCredential credential, List<LinkedCardInsert> cards) {
        try {
            codefCredentialMapper.insertAccountCredential(credential);
            for (LinkedCardInsert card : cards) {
                linkedCardMapper.insertLinkedCard(card.userCardId(), card.linkId(), card.userId(),
                        card.issuerId(), card.cardId(), card.cardNameFromCodef(), card.cardNo(),
                        card.codefCardKeyHash(), card.displayOrder());
            }
        } catch (DuplicateKeyException exception) {
            throw new CodefAccountAlreadyLinkedException();
        }
    }
}
