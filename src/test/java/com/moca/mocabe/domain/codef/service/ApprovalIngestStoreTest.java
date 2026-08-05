package com.moca.mocabe.domain.codef.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.moca.mocabe.domain.codef.mapper.CardApprovalMapper;
import com.moca.mocabe.domain.codef.model.ApprovalInsert;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class ApprovalIngestStoreTest {

    private CardApprovalMapper cardApprovalMapper;
    private ApprovalIngestStore store;

    @BeforeEach
    void setUp() {
        cardApprovalMapper = mock(CardApprovalMapper.class);
        store = new ApprovalIngestStore(cardApprovalMapper);
    }

    @Test
    @DisplayName("모든 신규 승인내역을 적재하고 적재 건수를 반환한다")
    void insertsAll() {
        doNothing().when(cardApprovalMapper).insertApproval(
                anyString(), anyString(), anyString(), any(), any(), any(), anyString(), anyInt(), anyString());

        int inserted = store.insertAll(List.of(insert("a-1"), insert("a-2")));

        assertEquals(2, inserted);
    }

    @Test
    @DisplayName("UNIQUE 충돌이 나는 건은 건너뛰고 나머지만 적재한다")
    void skipsDuplicates() {
        doThrow(new DuplicateKeyException("dup")).doNothing().when(cardApprovalMapper).insertApproval(
                anyString(), anyString(), anyString(), any(), any(), any(), anyString(), anyInt(), anyString());

        int inserted = store.insertAll(List.of(insert("a-1"), insert("a-2")));

        assertEquals(1, inserted);
    }

    private ApprovalInsert insert(String id) {
        return new ApprovalInsert(id, "user-1", "uc-1", null, "app-" + id,
                LocalDateTime.of(2026, 8, 1, 3, 0), "가맹점", 1000, "{}");
    }
}
