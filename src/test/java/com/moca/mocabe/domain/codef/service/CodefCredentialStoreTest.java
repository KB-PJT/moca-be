package com.moca.mocabe.domain.codef.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.moca.mocabe.domain.codef.dto.CreateCardLinkRequest;
import com.moca.mocabe.domain.codef.exception.CardAlreadyLinkedException;
import com.moca.mocabe.domain.codef.exception.CodefAccountAlreadyLinkedException;
import com.moca.mocabe.domain.codef.mapper.CodefCredentialMapper;
import com.moca.mocabe.domain.codef.mapper.LinkedCardMapper;
import com.moca.mocabe.domain.codef.model.CodefAccountCredential;
import com.moca.mocabe.domain.codef.model.LinkedCardInsert;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

class CodefCredentialStoreTest {

    private CodefCredentialMapper mapper;
    private LinkedCardMapper linkedCardMapper;
    private CodefCredentialStore store;

    @BeforeEach
    void setUp() {
        mapper = org.mockito.Mockito.mock(CodefCredentialMapper.class);
        linkedCardMapper = org.mockito.Mockito.mock(LinkedCardMapper.class);
        store = new CodefCredentialStore(mapper, linkedCardMapper);
    }

    @Test
    @DisplayName("자격정보 INSERT를 매퍼에 위임한다")
    void savesCredential() {
        CodefAccountCredential credential = new CodefAccountCredential();

        store.saveCredential(credential);

        verify(mapper).insertAccountCredential(credential);
        verifyNoInteractions(linkedCardMapper);
    }

    @Test
    @DisplayName("pending 카드번호/비밀번호 복구를 매퍼에 위임한다")
    void restoresPendingCardCredentials() {
        byte[] pendingCardNumberEnc = {1, 2};
        byte[] pendingCardPasswordEnc = {3, 4};

        store.restorePendingCardCredentials("link-1", "user-1", pendingCardNumberEnc, pendingCardPasswordEnc);

        verify(mapper).restorePendingCardCredentials(
                "link-1", "user-1", pendingCardNumberEnc, pendingCardPasswordEnc);
        verifyNoInteractions(linkedCardMapper);
    }

    @Test
    @DisplayName("자격정보 UNIQUE 충돌은 계정 중복 오류로 변환하고 원인 예외를 보존한다")
    void convertsCredentialDuplicateKeyException() {
        CodefAccountCredential credential = new CodefAccountCredential();
        DuplicateKeyException cause = new DuplicateKeyException("duplicate");
        org.mockito.Mockito.doThrow(cause).when(mapper).insertAccountCredential(credential);

        CodefAccountAlreadyLinkedException exception = assertThrows(
                CodefAccountAlreadyLinkedException.class, () -> store.saveCredential(credential));

        assertSame(cause, exception.getCause());
    }

    @Test
    @DisplayName("매칭 카드 INSERT를 매퍼에 위임하고 이번에 적재한 user_card_id를 반환한다")
    void savesCard() {
        LinkedCardInsert card = new LinkedCardInsert(
                "uc-1", "link-1", "user-1", "issuer-1", "card-1", "노리2 체크카드", "1234****5678", "hash-1", 0,
                null, null, false);

        String userCardId = store.saveCard(card);

        assertEquals("uc-1", userCardId);
        verify(linkedCardMapper).insertLinkedCard(
                "uc-1", "link-1", "user-1", "issuer-1", "card-1", "노리2 체크카드", "1234****5678", "hash-1", 0,
                null, null, false);
        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("동시 재조회로 다른 요청이 먼저 적재했다면(UNIQUE 충돌) 새로 적재하지 않고 기존 user_card_id를 반환한다")
    void returnsExistingUserCardIdWhenConcurrentRequestWinsRace() {
        LinkedCardInsert card = new LinkedCardInsert(
                "uc-new", "link-1", "user-1", "issuer-1", "card-1", "노리2 체크카드", "1234****5678", "hash-1", 0,
                null, null, false);
        DuplicateKeyException cause = new DuplicateKeyException("duplicate");
        org.mockito.Mockito.doThrow(cause).when(linkedCardMapper).insertLinkedCard(
                "uc-new", "link-1", "user-1", "issuer-1", "card-1", "노리2 체크카드", "1234****5678", "hash-1", 0,
                null, null, false);
        when(linkedCardMapper.findUserCardIdByUserIdAndCardKeyHash("user-1", "hash-1")).thenReturn("uc-winner");

        String userCardId = store.saveCard(card);

        assertEquals("uc-winner", userCardId);
    }

    @Test
    @DisplayName("UNIQUE 충돌인데 기존 행을 찾지 못하면 카드 중복 오류로 알린다")
    void throwsWhenDuplicateKeyButNoExistingRowFound() {
        LinkedCardInsert card = new LinkedCardInsert(
                "uc-new", "link-1", "user-1", "issuer-1", "card-1", "노리2 체크카드", "1234****5678", "hash-1", 0,
                null, null, false);
        DuplicateKeyException cause = new DuplicateKeyException("duplicate");
        org.mockito.Mockito.doThrow(cause).when(linkedCardMapper).insertLinkedCard(
                "uc-new", "link-1", "user-1", "issuer-1", "card-1", "노리2 체크카드", "1234****5678", "hash-1", 0,
                null, null, false);
        when(linkedCardMapper.findUserCardIdByUserIdAndCardKeyHash("user-1", "hash-1")).thenReturn(null);

        CardAlreadyLinkedException exception = assertThrows(
                CardAlreadyLinkedException.class, () -> store.saveCard(card));

        assertSame(cause, exception.getCause());
        assertEquals("이미 등록된 보유카드입니다.", exception.getMessage());
    }

    @Test
    @DisplayName("외부 연동은 트랜잭션 밖에서 수행하고 저장 메서드만 트랜잭션을 선언한다")
    void declaresTransactionOnlyOnStore() throws NoSuchMethodException {
        Method createLink = CardLinkService.class.getMethod(
                "createLink", String.class, CreateCardLinkRequest.class);
        Method saveCredential = CodefCredentialStore.class.getMethod(
                "saveCredential", CodefAccountCredential.class);
        Method saveCard = CodefCredentialStore.class.getMethod("saveCard", LinkedCardInsert.class);

        assertNull(createLink.getAnnotation(Transactional.class));
        assertNotNull(saveCredential.getAnnotation(Transactional.class));
        assertNotNull(saveCard.getAnnotation(Transactional.class));
    }
}
