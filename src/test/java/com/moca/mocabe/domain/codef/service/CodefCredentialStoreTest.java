package com.moca.mocabe.domain.codef.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.moca.mocabe.domain.codef.dto.CreateCardLinkRequest;
import com.moca.mocabe.domain.codef.exception.CardAlreadyLinkedException;
import com.moca.mocabe.domain.codef.exception.CodefAccountAlreadyLinkedException;
import com.moca.mocabe.domain.codef.mapper.CodefCredentialMapper;
import com.moca.mocabe.domain.codef.mapper.LinkedCardMapper;
import com.moca.mocabe.domain.codef.model.CodefAccountCredential;
import com.moca.mocabe.domain.codef.model.LinkedCardInsert;
import java.lang.reflect.Method;
import java.util.List;
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
    @DisplayName("자격정보와 매칭 카드 INSERT를 각 매퍼에 위임한다")
    void savesCredentialAndCards() {
        CodefAccountCredential credential = new CodefAccountCredential();
        LinkedCardInsert card = new LinkedCardInsert(
                "uc-1", "link-1", "user-1", "issuer-1", "card-1", "노리2 체크카드", "1234****5678", "hash-1", 0);

        store.save(credential, List.of(card));

        verify(mapper).insertAccountCredential(credential);
        verify(linkedCardMapper).insertLinkedCard(
                "uc-1", "link-1", "user-1", "issuer-1", "card-1", "노리2 체크카드", "1234****5678", "hash-1", 0);
    }

    @Test
    @DisplayName("자격정보 UNIQUE 충돌은 계정 중복 오류로 변환하고 원인 예외를 보존하며, 카드는 적재를 시도하지 않는다")
    void convertsCredentialDuplicateKeyException() {
        CodefAccountCredential credential = new CodefAccountCredential();
        LinkedCardInsert card = new LinkedCardInsert(
                "uc-1", "link-1", "user-1", "issuer-1", "card-1", "노리2 체크카드", "1234****5678", "hash-1", 0);
        DuplicateKeyException cause = new DuplicateKeyException("duplicate");
        org.mockito.Mockito.doThrow(cause).when(mapper).insertAccountCredential(credential);

        CodefAccountAlreadyLinkedException exception = assertThrows(
                CodefAccountAlreadyLinkedException.class, () -> store.save(credential, List.of(card)));

        assertSame(cause, exception.getCause());
        verifyNoInteractions(linkedCardMapper);
    }

    @Test
    @DisplayName("보유카드 UNIQUE 충돌은 계정 중복이 아니라 카드 중복 오류로 구분하고 원인 예외를 보존한다")
    void convertsCardDuplicateKeyExceptionSeparatelyFromCredential() {
        CodefAccountCredential credential = new CodefAccountCredential();
        LinkedCardInsert card = new LinkedCardInsert(
                "uc-1", "link-1", "user-1", "issuer-1", "card-1", "노리2 체크카드", "1234****5678", "hash-1", 0);
        DuplicateKeyException cause = new DuplicateKeyException("duplicate");
        org.mockito.Mockito.doThrow(cause).when(linkedCardMapper).insertLinkedCard(
                "uc-1", "link-1", "user-1", "issuer-1", "card-1", "노리2 체크카드", "1234****5678", "hash-1", 0);

        CardAlreadyLinkedException exception = assertThrows(
                CardAlreadyLinkedException.class, () -> store.save(credential, List.of(card)));

        assertSame(cause, exception.getCause());
        assertEquals("이미 등록된 보유카드입니다.", exception.getMessage());
    }

    @Test
    @DisplayName("외부 연동은 트랜잭션 밖에서 수행하고 저장 메서드만 트랜잭션을 선언한다")
    void declaresTransactionOnlyOnStore() throws NoSuchMethodException {
        Method createLink = CardLinkService.class.getMethod(
                "createLink", String.class, CreateCardLinkRequest.class);
        Method save = CodefCredentialStore.class.getMethod(
                "save", CodefAccountCredential.class, List.class);

        assertNull(createLink.getAnnotation(Transactional.class));
        assertNotNull(save.getAnnotation(Transactional.class));
    }
}
