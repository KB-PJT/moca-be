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
     * discoverOwnedCards(2단계)가 claim(짧은 트랜잭션에서 pending을 읽고 바로 지움) 이후 CODEF 호출
     * 실패나 카탈로그 매칭 실패로 카드번호를 어느 카드에도 저장하지 못했을 때, claim 시점에 읽어둔
     * 값 그대로 pending을 되돌려 사용자가 다시 discover를 호출할 수 있게 하는 별도 트랜잭션이다.
     */
    @Transactional
    public void restorePendingCardCredentials(String linkId, String userId,
                                              byte[] pendingCardNumberEnc, byte[] pendingCardPasswordEnc) {
        codefCredentialMapper.restorePendingCardCredentials(
                linkId, userId, pendingCardNumberEnc, pendingCardPasswordEnc);
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
                    card.codefCardKeyHash(), card.displayOrder(),
                    card.cardNumberEnc(), card.cardPasswordEnc(), card.isActive());
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

    /** 회원 탈퇴 시 codef_account_credentials를 정리한다. user_cards가 이를 참조하므로 그 뒤에 호출해야 한다. */
    @Transactional
    public void deleteAllByUserId(String userId) {
        codefCredentialMapper.deleteAccountCredentialsByUserId(userId);
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
