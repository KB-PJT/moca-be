package com.moca.mocabe.domain.codef.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.moca.mocabe.domain.card.dto.SyncMyCardsResponse;
import com.moca.mocabe.domain.codef.exception.InvalidSyncPeriodException;
import com.moca.mocabe.domain.codef.infra.CodefClient;
import com.moca.mocabe.domain.codef.infra.Encryptor;
import com.moca.mocabe.domain.codef.mapper.CardApprovalMapper;
import com.moca.mocabe.domain.codef.mapper.CodefCredentialMapper;
import com.moca.mocabe.domain.codef.model.ApprovalInsert;
import com.moca.mocabe.domain.codef.model.CodefApproval;
import com.moca.mocabe.domain.codef.model.CodefConnection;
import com.moca.mocabe.domain.codef.model.ExistingApprovalKey;
import com.moca.mocabe.domain.codef.model.UserCardMatchRow;
import com.moca.mocabe.domain.merchant.service.MerchantCandidateSnapshot;
import com.moca.mocabe.domain.merchant.service.MerchantLookup;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CardSyncServiceTest {

    private static final String USER_ID = "user-1";

    private CodefClient codefClient;
    private CodefCredentialMapper codefCredentialMapper;
    private CardApprovalMapper cardApprovalMapper;
    private ApprovalCardMatcher approvalCardMatcher;
    private MerchantLookup merchantLookup;
    private MerchantCandidateSnapshot merchantCandidates;
    private ApprovalIngestStore approvalIngestStore;
    private Encryptor encryptor;
    private CardSyncService service;

    @BeforeEach
    void setUp() {
        codefClient = mock(CodefClient.class);
        codefCredentialMapper = mock(CodefCredentialMapper.class);
        cardApprovalMapper = mock(CardApprovalMapper.class);
        approvalCardMatcher = mock(ApprovalCardMatcher.class);
        merchantLookup = mock(MerchantLookup.class);
        merchantCandidates = mock(MerchantCandidateSnapshot.class);
        approvalIngestStore = mock(ApprovalIngestStore.class);
        encryptor = mock(Encryptor.class);
        when(merchantLookup.loadCandidates()).thenReturn(merchantCandidates);
        service = new CardSyncService(codefClient, codefCredentialMapper, cardApprovalMapper,
                approvalCardMatcher, merchantLookup, approvalIngestStore, encryptor);
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦으면 예외를 던지고 조회하지 않는다")
    void throwsWhenPeriodInvalid() {
        assertThrows(InvalidSyncPeriodException.class,
                () -> service.sync(USER_ID, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 1)));
        verifyNoInteractions(codefClient, cardApprovalMapper, codefCredentialMapper);
    }

    @Test
    @DisplayName("보유카드가 없으면 CODEF를 조회하지 않고 0건을 반환한다")
    void returnsZeroWhenNoUserCards() {
        when(cardApprovalMapper.findUserCardsForMatching(USER_ID)).thenReturn(List.of());
        when(codefCredentialMapper.findActiveConnectionsByUserId(USER_ID))
                .thenReturn(List.of(connection()));

        SyncMyCardsResponse response = service.sync(USER_ID, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3));

        assertEquals(0, response.getSyncedCardCount());
        assertEquals(0, response.getSyncedApprovalCount());
        verifyNoInteractions(codefClient);
    }

    @Test
    @DisplayName("활성 연동이 없으면 CODEF를 조회하지 않고 카드 수만 반환한다")
    void returnsZeroApprovalsWhenNoConnections() {
        when(cardApprovalMapper.findUserCardsForMatching(USER_ID)).thenReturn(List.of(userCard()));
        when(codefCredentialMapper.findActiveConnectionsByUserId(USER_ID)).thenReturn(List.of());

        SyncMyCardsResponse response = service.sync(USER_ID, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3));

        assertEquals(1, response.getSyncedCardCount());
        assertEquals(0, response.getSyncedApprovalCount());
        verifyNoInteractions(codefClient);
    }

    @Test
    @DisplayName("날짜를 생략하면 이번 달 1일부터 오늘까지 조회한다")
    void usesCurrentMonthByDefault() {
        when(cardApprovalMapper.findUserCardsForMatching(USER_ID)).thenReturn(List.of(userCard()));
        when(codefCredentialMapper.findActiveConnectionsByUserId(USER_ID)).thenReturn(List.of(connection()));
        when(encryptor.decrypt(any())).thenReturn("900101");
        when(cardApprovalMapper.findExistingApprovalKeys(eq(USER_ID), any(), any())).thenReturn(List.of());
        when(codefClient.getApprovals(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of());
        when(approvalIngestStore.insertAll(any())).thenReturn(0);

        ArgumentCaptor<String> startCaptor = ArgumentCaptor.forClass(String.class);
        SyncMyCardsResponse response = service.sync(USER_ID, null, null);

        verify(codefClient).getApprovals(eq("cid"), eq("0301"), eq("900101"),
                startCaptor.capture(), anyString());
        assertTrue(startCaptor.getValue().endsWith("01"));
        assertEquals(1, response.getSyncedCardCount());
    }

    @Test
    @DisplayName("정상 국내 승인건만 매칭·중복제거해 신규만 적재한다")
    void ingestsOnlyNewNormalDomesticApprovals() {
        when(cardApprovalMapper.findUserCardsForMatching(USER_ID)).thenReturn(List.of(userCard()));
        when(codefCredentialMapper.findActiveConnectionsByUserId(USER_ID)).thenReturn(List.of(connection()));
        when(encryptor.decrypt(any())).thenReturn("900101");
        when(cardApprovalMapper.findExistingApprovalKeys(eq(USER_ID), any(), any())).thenReturn(List.of(
                new ExistingApprovalKey("uc-1", "999", LocalDateTime.of(2026, 8, 2, 0, 0), 100, "x"),
                new ExistingApprovalKey("uc-1", null, LocalDateTime.of(2026, 8, 4, 15, 0), 7000, "노포")));
        when(approvalCardMatcher.match(any(), any(), eq("issuer-1"))).thenAnswer(invocation -> {
            CodefApproval approval = invocation.getArgument(1);
            return "미매칭".equals(approval.memberStoreName()) ? null : "uc-1";
        });
        when(merchantCandidates.resolveMerchantId("스타벅스")).thenReturn("m-1");
        when(codefClient.getApprovals(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(
                        approval("20260801", "120000", "카드A", "스타벅스", "5,000", "100", "1", "0"),
                        approval("20260801", "120000", "카드A", "스타벅스", "5,000", "100", "1", "0"),
                        approval("20260801", "120000", "카드A", "환불", "5,000", "150", "1", "1"),
                        approval("20260801", "120000", "카드A", "해외", "5,000", "160", "2", "0"),
                        approval("20260801", "120000", "카드A", "미매칭", "5,000", "200", "1", "0"),
                        approval("20260841", "120000", "카드A", "잘못된날짜", "5,000", "300", "1", "0"),
                        approval("2026", "120000", "카드A", "짧은날짜", "5,000", "301", "1", "0"),
                        approval("20260801", "120000", "카드A", "금액오류", "abc", "400", "1", "0"),
                        approval("20260801", "120000", "카드A", "대시금액", "-", "401", "1", "0"),
                        approval("20260801", "120000", "카드A", "널금액", null, "402", "1", "0"),
                        approval("20260801", "120000", "카드A", "오버플로우", "99999999999999", "500", "1", "0"),
                        approval("20260802", "120000", "카드A", "이미적재", "100", "999", "1", "0"),
                        approval("20260803", "153000", "카드A", "노포2", "3000", null, "1", "0"),
                        approval("20260805", "", "카드A", "노포", "7000", null, "1", "0")));

        when(approvalIngestStore.insertAll(any()))
                .thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());

        SyncMyCardsResponse response = service.sync(USER_ID, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 5));

        ArgumentCaptor<List<ApprovalInsert>> captor = ArgumentCaptor.forClass(List.class);
        verify(approvalIngestStore).insertAll(captor.capture());
        List<ApprovalInsert> inserted = captor.getValue();
        assertEquals(2, inserted.size());
        assertEquals(2, response.getSyncedApprovalCount());
        assertEquals(1, response.getSyncedCardCount());
        ApprovalInsert first = inserted.get(0);
        assertEquals("스타벅스", first.merchantName());
        assertEquals(5000, first.amount());
        assertEquals("m-1", first.merchantId());
        assertEquals("100", first.approvalNumber());
        ApprovalInsert second = inserted.get(1);
        assertEquals("노포2", second.merchantName());
        assertEquals(3000, second.amount());
        assertEquals(null, second.merchantId());
        assertEquals(null, second.approvalNumber());
        assertTrue(response.getSyncedAt().contains("+09:00"));
        // 승인건이 14개(가맹점 매칭 대상만 여럿)여도 가맹점 후보 스냅샷은 동기화당 한 번만 조회해야 한다.
        verify(merchantLookup, times(1)).loadCandidates();
    }

    private CodefApproval approval(String usedDate, String usedTime, String cardName, String store,
                                   String amount, String approvalNo, String homeForeign, String cancel) {
        return new CodefApproval(usedDate, usedTime, "1234****5678", cardName, store, amount,
                approvalNo, homeForeign, cancel, "{\"resMemberStoreName\":\"" + store + "\"}");
    }

    private CodefConnection connection() {
        return new CodefConnection(
                "link-1", "cid", "0301", "issuer-1", null, null, new byte[]{1, 2, 3});
    }

    private UserCardMatchRow userCard() {
        return new UserCardMatchRow("uc-1", "issuer-1", "카드A", "1234****5678");
    }
}
