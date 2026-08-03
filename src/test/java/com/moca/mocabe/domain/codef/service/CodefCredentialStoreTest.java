package com.moca.mocabe.domain.codef.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

import com.moca.mocabe.domain.codef.dto.CreateCardLinkRequest;
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
    @DisplayName("UNIQUE 충돌을 중복 연동 오류로 변환한다")
    void convertsDuplicateKeyException() {
        CodefAccountCredential credential = new CodefAccountCredential();
        org.mockito.Mockito.doThrow(new DuplicateKeyException("duplicate"))
                .when(mapper).insertAccountCredential(credential);

        assertThrows(CodefAccountAlreadyLinkedException.class, () -> store.save(credential, List.of()));
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
