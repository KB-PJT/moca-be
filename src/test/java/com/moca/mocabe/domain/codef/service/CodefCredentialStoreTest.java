package com.moca.mocabe.domain.codef.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

import com.moca.mocabe.domain.codef.dto.CreateCardLinkRequest;
import com.moca.mocabe.domain.codef.exception.CodefAccountAlreadyLinkedException;
import com.moca.mocabe.domain.codef.mapper.CodefCredentialMapper;
import com.moca.mocabe.domain.codef.model.CodefAccountCredential;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

class CodefCredentialStoreTest {

    private CodefCredentialMapper mapper;
    private CodefCredentialStore store;

    @BeforeEach
    void setUp() {
        mapper = org.mockito.Mockito.mock(CodefCredentialMapper.class);
        store = new CodefCredentialStore(mapper);
    }

    @Test
    @DisplayName("자격정보 INSERT를 저장 매퍼에 위임한다")
    void savesCredential() {
        CodefAccountCredential credential = new CodefAccountCredential();

        store.save(credential);

        verify(mapper).insertAccountCredential(credential);
    }

    @Test
    @DisplayName("UNIQUE 충돌을 중복 연동 오류로 변환한다")
    void convertsDuplicateKeyException() {
        CodefAccountCredential credential = new CodefAccountCredential();
        org.mockito.Mockito.doThrow(new DuplicateKeyException("duplicate"))
                .when(mapper).insertAccountCredential(credential);

        assertThrows(CodefAccountAlreadyLinkedException.class, () -> store.save(credential));
    }

    @Test
    @DisplayName("외부 연동은 트랜잭션 밖에서 수행하고 저장 메서드만 트랜잭션을 선언한다")
    void declaresTransactionOnlyOnStore() throws NoSuchMethodException {
        Method createLink = CardLinkService.class.getMethod(
                "createLink", String.class, CreateCardLinkRequest.class);
        Method save = CodefCredentialStore.class.getMethod("save", CodefAccountCredential.class);

        assertNull(createLink.getAnnotation(Transactional.class));
        assertNotNull(save.getAnnotation(Transactional.class));
    }
}
