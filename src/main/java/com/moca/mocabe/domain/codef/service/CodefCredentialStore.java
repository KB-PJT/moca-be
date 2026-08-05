package com.moca.mocabe.domain.codef.service;

import com.moca.mocabe.domain.codef.exception.CardAlreadyLinkedException;
import com.moca.mocabe.domain.codef.exception.CodefAccountAlreadyLinkedException;
import com.moca.mocabe.domain.codef.mapper.CodefCredentialMapper;
import com.moca.mocabe.domain.codef.mapper.LinkedCardMapper;
import com.moca.mocabe.domain.codef.model.CodefAccountCredential;
import com.moca.mocabe.domain.codef.model.LinkedCardInsert;
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

    /** connectedId 발급 직후 자격정보만 우선 커밋한다. 이후 보유카드 조회가 실패해도 이 저장은 보존된다. */
    @Transactional
    public void saveCredential(CodefAccountCredential credential) {
        insertCredential(credential);
    }

    /**
     * 매칭된 보유카드 한 건을 자격정보와 별도 트랜잭션으로 적재하고 최종 user_card_id를 반환한다.
     * 동시에 같은 카드를 재조회하는 다른 요청이 먼저 적재했다면((user_id, codef_card_key_hash)
     * UNIQUE 위반) 새로 적재하지 않고 그 요청이 이미 적재한 user_card_id를 그대로 돌려준다.
     */
    @Transactional
    public String saveCard(LinkedCardInsert card) {
        try {
            linkedCardMapper.insertLinkedCard(card.userCardId(), card.linkId(), card.userId(),
                    card.issuerId(), card.cardId(), card.cardNameFromCodef(), card.cardNo(),
                    card.codefCardKeyHash(), card.displayOrder());
            return card.userCardId();
        } catch (DuplicateKeyException exception) {
            String existingUserCardId = linkedCardMapper.findUserCardIdByUserIdAndCardKeyHash(
                    card.userId(), card.codefCardKeyHash());
            if (existingUserCardId == null) {
                // UNIQUE 위반인데 같은 트랜잭션에서 기존 행을 못 찾으면 동시 재조회로 설명되지 않는
                // 상황이라 원래대로 카드 중복 오류로 알린다.
                throw new CardAlreadyLinkedException(exception);
            }
            return existingUserCardId;
        }
    }

    private void insertCredential(CodefAccountCredential credential) {
        try {
            codefCredentialMapper.insertAccountCredential(credential);
        } catch (DuplicateKeyException exception) {
            // credential_identity_hash UNIQUE 위반 = 같은 카드사 계정을 이미 연동함
            throw new CodefAccountAlreadyLinkedException(exception);
        }
    }
}
