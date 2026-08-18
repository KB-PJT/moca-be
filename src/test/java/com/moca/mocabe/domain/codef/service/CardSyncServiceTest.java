package com.moca.mocabe.domain.codef.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.moca.mocabe.domain.benefit.service.BenefitUsageCalculationService;
import com.moca.mocabe.domain.card.dto.SyncMyCardsResponse;
import com.moca.mocabe.domain.codef.exception.ApprovalSyncFailedException;
import com.moca.mocabe.domain.codef.exception.CodefConnectionNotFoundException;
import com.moca.mocabe.domain.codef.exception.CodefUnavailableException;
import com.moca.mocabe.domain.codef.exception.InvalidSyncPeriodException;
import com.moca.mocabe.domain.codef.exception.PerformanceSyncFailedException;
import com.moca.mocabe.domain.codef.exception.PerformanceUnsupportedException;
import com.moca.mocabe.domain.codef.infra.CodefClient;
import com.moca.mocabe.domain.codef.infra.Encryptor;
import com.moca.mocabe.domain.codef.mapper.CardApprovalMapper;
import com.moca.mocabe.domain.codef.mapper.CodefCredentialMapper;
import com.moca.mocabe.domain.codef.model.ActiveCardCredential;
import com.moca.mocabe.domain.codef.model.ApprovalInsert;
import com.moca.mocabe.domain.codef.model.CodefApproval;
import com.moca.mocabe.domain.codef.model.CodefCardPerformance;
import com.moca.mocabe.domain.codef.model.CodefConnection;
import com.moca.mocabe.domain.codef.model.ExistingApprovalKey;
import com.moca.mocabe.domain.codef.model.PerformanceSnapshotUpsert;
import com.moca.mocabe.domain.codef.model.UserCardMatchRow;
import com.moca.mocabe.domain.merchant.service.MerchantCandidateSnapshot;
import com.moca.mocabe.domain.merchant.service.MerchantLookup;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
  private PerformanceSnapshotStore performanceSnapshotStore;
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
    performanceSnapshotStore = mock(PerformanceSnapshotStore.class);
    encryptor = mock(Encryptor.class);
    when(merchantLookup.loadCandidates()).thenReturn(merchantCandidates);
    // 매칭된 활성 카드가 있는 연동을 기본값으로 둔다. requiresCardNo=false 연동도 이제 활성 카드가
    // 있어야 CODEF를 호출하므로, 개별 테스트에서 다른 credentialId를 스텁하지 않는 한 이 기본값을 쓴다.
    when(cardApprovalMapper.findActiveCardCredentialsByCredentialId(anyString()))
        .thenReturn(List.of(new ActiveCardCredential("uc-1", new byte[] {1}, null)));
    service =
        new CardSyncService(
            codefClient,
            codefCredentialMapper,
            cardApprovalMapper,
            approvalCardMatcher,
            merchantLookup,
            approvalIngestStore,
            performanceSnapshotStore,
            encryptor);
  }

  @Test
  @DisplayName("destroy()를 호출하면 CODEF 조회용 스레드풀을 종료한다")
  void destroyShutsDownFetchExecutor() {
    assertDoesNotThrow(() -> service.destroy());
  }

  @Test
  @DisplayName("시작일이 종료일보다 늦으면 예외를 던지고 조회하지 않는다")
  void throwsWhenPeriodInvalid() {
    assertThrows(
        InvalidSyncPeriodException.class,
        () -> service.sync(USER_ID, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 1)));
    verifyNoInteractions(codefClient, cardApprovalMapper, codefCredentialMapper);
  }

  @Test
  @DisplayName("보유카드가 없으면 CODEF를 조회하지 않고 0건을 반환한다")
  void returnsZeroWhenNoUserCards() {
    when(cardApprovalMapper.findUserCardsForMatching(USER_ID)).thenReturn(List.of());
    when(codefCredentialMapper.findActiveConnectionsByUserId(USER_ID))
        .thenReturn(List.of(connection()));

    SyncMyCardsResponse response =
        service.sync(USER_ID, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3));

    assertEquals(0, response.getSyncedCardCount());
    assertEquals(0, response.getSyncedApprovalCount());
    verifyNoInteractions(codefClient);
  }

  @Test
  @DisplayName("활성 연동이 없으면 CODEF를 조회하지 않고 카드 수만 반환한다")
  void returnsZeroApprovalsWhenNoConnections() {
    when(cardApprovalMapper.findUserCardsForMatching(USER_ID)).thenReturn(List.of(userCard()));
    when(codefCredentialMapper.findActiveConnectionsByUserId(USER_ID)).thenReturn(List.of());

    SyncMyCardsResponse response =
        service.sync(USER_ID, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3));

    assertEquals(1, response.getSyncedCardCount());
    assertEquals(0, response.getSyncedApprovalCount());
    verifyNoInteractions(codefClient);
  }

  @Test
  @DisplayName("카드번호 불필요 연동이라도 매칭된 활성 카드가 없으면 CODEF를 조회하지 않는다")
  void skipsConnectionWithoutActiveCardsEvenWhenCardNoNotRequired() {
    when(cardApprovalMapper.findUserCardsForMatching(USER_ID)).thenReturn(List.of(userCard()));
    when(codefCredentialMapper.findActiveConnectionsByUserId(USER_ID))
        .thenReturn(List.of(connection()));
    when(cardApprovalMapper.findActiveCardCredentialsByCredentialId("link-1"))
        .thenReturn(List.of());

    SyncMyCardsResponse response =
        service.sync(USER_ID, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3));

    assertEquals(1, response.getSyncedCardCount());
    assertEquals(0, response.getSyncedApprovalCount());
    verifyNoInteractions(codefClient);
  }

  @Test
  @DisplayName("혜택 계산이 활성화되어도 새 승인 건이 없으면 계산 파이프라인을 빈 목록으로 완료한다")
  void completesEnabledBenefitCalculationPipelineWithoutInsertedApprovals() {
    BenefitUsageCalculationService calculationService = mock(BenefitUsageCalculationService.class);
    when(calculationService.isEnabled()).thenReturn(true);
    service =
        new CardSyncService(
            codefClient,
            codefCredentialMapper,
            cardApprovalMapper,
            approvalCardMatcher,
            merchantLookup,
            approvalIngestStore,
            performanceSnapshotStore,
            encryptor,
            calculationService);
    when(cardApprovalMapper.findUserCardsForMatching(USER_ID)).thenReturn(List.of(userCard()));
    when(codefCredentialMapper.findActiveConnectionsByUserId(USER_ID))
        .thenReturn(List.of(connection()));
    when(encryptor.decrypt(any())).thenReturn("900101");
    when(cardApprovalMapper.findExistingApprovalKeys(eq(USER_ID), any(), any()))
        .thenReturn(List.of());
    when(codefClient.getApprovals(
            anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(List.of());
    when(approvalIngestStore.insertAllReturningInserted(any())).thenReturn(List.of());

    service.sync(USER_ID, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3));

    verify(approvalIngestStore).insertAllReturningInserted(any());
    verify(calculationService).calculateAndPersist(List.of());
  }

  @Test
  @DisplayName("날짜를 생략하면 이번 달 1일부터 오늘까지 조회한다")
  void usesCurrentMonthByDefault() {
    when(cardApprovalMapper.findUserCardsForMatching(USER_ID)).thenReturn(List.of(userCard()));
    when(codefCredentialMapper.findActiveConnectionsByUserId(USER_ID))
        .thenReturn(List.of(connection()));
    when(encryptor.decrypt(any())).thenReturn("900101");
    when(cardApprovalMapper.findExistingApprovalKeys(eq(USER_ID), any(), any()))
        .thenReturn(List.of());
    when(codefClient.getApprovals(
            anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(List.of());
    when(approvalIngestStore.insertAll(any())).thenReturn(0);

    ArgumentCaptor<String> startCaptor = ArgumentCaptor.forClass(String.class);
    SyncMyCardsResponse response = service.sync(USER_ID, null, null);

    verify(codefClient)
        .getApprovals(
            eq("cid"), eq("0301"), eq("900101"), startCaptor.capture(), anyString(), any(), any());
    assertTrue(startCaptor.getValue().endsWith("01"));
    assertEquals(1, response.getSyncedCardCount());
  }

  @Test
  @DisplayName("정상 국내 승인건만 매칭·중복제거해 신규만 적재한다")
  void ingestsOnlyNewNormalDomesticApprovals() {
    when(cardApprovalMapper.findUserCardsForMatching(USER_ID)).thenReturn(List.of(userCard()));
    when(codefCredentialMapper.findActiveConnectionsByUserId(USER_ID))
        .thenReturn(List.of(connection()));
    when(encryptor.decrypt(any())).thenReturn("900101");
    when(cardApprovalMapper.findExistingApprovalKeys(eq(USER_ID), any(), any()))
        .thenReturn(
            List.of(
                new ExistingApprovalKey(
                    "uc-1", "999", LocalDateTime.of(2026, 8, 2, 0, 0), 100, "x"),
                new ExistingApprovalKey(
                    "uc-1", null, LocalDateTime.of(2026, 8, 4, 15, 0), 7000, "노포")));
    when(approvalCardMatcher.match(any(), any(), eq("issuer-1")))
        .thenAnswer(
            invocation -> {
              CodefApproval approval = invocation.getArgument(1);
              return "미매칭".equals(approval.memberStoreName()) ? null : "uc-1";
            });
    when(merchantCandidates.resolveMerchantId("스타벅스")).thenReturn("m-1");
    when(codefClient.getApprovals(
            anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(
            List.of(
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

    SyncMyCardsResponse response =
        service.sync(USER_ID, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 5));

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

  @Test
  @DisplayName("실적을 조회해 매칭된 카드만 대표값(최대 현재이용금액)을 upsert하고 건수를 반환한다")
  void ingestsMatchedPerformanceSnapshots() {
    when(cardApprovalMapper.findUserCardsForMatching(USER_ID)).thenReturn(List.of(userCard()));
    when(codefCredentialMapper.findActiveConnectionsByUserId(USER_ID))
        .thenReturn(List.of(connection()));
    when(encryptor.decrypt(any())).thenReturn("900101");
    when(cardApprovalMapper.findExistingApprovalKeys(eq(USER_ID), any(), any()))
        .thenReturn(List.of());
    when(codefClient.getApprovals(
            anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(List.of());
    when(approvalIngestStore.insertAll(any())).thenReturn(0);
    when(codefClient.getPerformance(eq("cid"), eq("0301"), eq("900101"), any(), any(), anyString()))
        .thenReturn(
            List.of(
                new CodefCardPerformance("카드A", "1234****5678", 300000),
                new CodefCardPerformance("미매칭카드", "0000****0000", 100000),
                new CodefCardPerformance("혜택없음", "9999****9999", null)));
    when(approvalCardMatcher.match(any(), eq("카드A"), eq("1234****5678"), eq("issuer-1")))
        .thenReturn("uc-1");
    when(approvalCardMatcher.match(any(), eq("미매칭카드"), eq("0000****0000"), eq("issuer-1")))
        .thenReturn(null);
    when(performanceSnapshotStore.upsertAll(any()))
        .thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());

    SyncMyCardsResponse response =
        service.sync(USER_ID, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3));

    ArgumentCaptor<List<PerformanceSnapshotUpsert>> captor = ArgumentCaptor.forClass(List.class);
    verify(performanceSnapshotStore).upsertAll(captor.capture());
    List<PerformanceSnapshotUpsert> upserts = captor.getValue();
    assertEquals(1, upserts.size());
    assertEquals("uc-1", upserts.get(0).userCardId());
    assertEquals(300000, upserts.get(0).currentSpendAmount());
    assertTrue(upserts.get(0).performanceMonth().matches("\\d{4}-\\d{2}"));
    assertEquals(1, response.getSyncedPerformanceCount());
  }

  @Test
  @DisplayName("조회 가능 범위가 충분하면 지난 달 실적도 함께 조회해 대상 월과 별도로 upsert한다")
  void ingestsPreviousMonthPerformanceWhenLookbackAllows() {
    CodefConnection connection =
        new CodefConnection(
            "link-1", "cid", "0301", "issuer-1", "KB카드", 12, new byte[] {1, 2, 3}, false, false);
    when(cardApprovalMapper.findUserCardsForMatching(USER_ID)).thenReturn(List.of(userCard()));
    when(codefCredentialMapper.findActiveConnectionsByUserId(USER_ID))
        .thenReturn(List.of(connection));
    when(encryptor.decrypt(any())).thenReturn("900101");
    when(cardApprovalMapper.findExistingApprovalKeys(eq(USER_ID), any(), any()))
        .thenReturn(List.of());
    when(codefClient.getApprovals(
            anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(List.of());
    when(approvalIngestStore.insertAll(any())).thenReturn(0);
    when(codefClient.getPerformance(eq("cid"), eq("0301"), eq("900101"), any(), any(), eq("202608")))
        .thenReturn(List.of(new CodefCardPerformance("카드A", "1234****5678", 300000)));
    when(codefClient.getPerformance(eq("cid"), eq("0301"), eq("900101"), any(), any(), eq("202607")))
        .thenReturn(List.of(new CodefCardPerformance("카드A", "1234****5678", 200000)));
    when(approvalCardMatcher.match(any(), eq("카드A"), eq("1234****5678"), eq("issuer-1")))
        .thenReturn("uc-1");
    when(performanceSnapshotStore.upsertAll(any()))
        .thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());

    SyncMyCardsResponse response =
        service.sync(USER_ID, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3));

    ArgumentCaptor<List<PerformanceSnapshotUpsert>> captor = ArgumentCaptor.forClass(List.class);
    verify(performanceSnapshotStore).upsertAll(captor.capture());
    List<PerformanceSnapshotUpsert> upserts = captor.getValue();
    assertEquals(2, upserts.size());
    assertTrue(upserts.stream().anyMatch(u -> u.performanceMonth().equals("2026-08")
        && u.currentSpendAmount() == 300000));
    assertTrue(upserts.stream().anyMatch(u -> u.performanceMonth().equals("2026-07")
        && u.currentSpendAmount() == 200000));
    assertEquals(2, response.getSyncedPerformanceCount());
  }

  @Test
  @DisplayName("지난 달 실적조회 호출 자체가 실패해도 건너뛸 뿐 대상 월 동기화는 성공한다")
  void skipsPreviousMonthPerformanceWhenCodefCallFails() {
    CodefConnection connection =
        new CodefConnection(
            "link-1", "cid", "0301", "issuer-1", "KB카드", 12, new byte[] {1, 2, 3}, false, false);
    when(cardApprovalMapper.findUserCardsForMatching(USER_ID)).thenReturn(List.of(userCard()));
    when(codefCredentialMapper.findActiveConnectionsByUserId(USER_ID))
        .thenReturn(List.of(connection));
    when(encryptor.decrypt(any())).thenReturn("900101");
    when(cardApprovalMapper.findExistingApprovalKeys(eq(USER_ID), any(), any()))
        .thenReturn(List.of());
    when(codefClient.getApprovals(
            anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(List.of());
    when(approvalIngestStore.insertAll(any())).thenReturn(0);
    when(codefClient.getPerformance(eq("cid"), eq("0301"), eq("900101"), any(), any(), eq("202608")))
        .thenReturn(List.of(new CodefCardPerformance("카드A", "1234****5678", 300000)));
    when(codefClient.getPerformance(eq("cid"), eq("0301"), eq("900101"), any(), any(), eq("202607")))
        .thenThrow(new CodefUnavailableException("CODEF 실적조회에 실패했습니다."));
    when(approvalCardMatcher.match(any(), eq("카드A"), eq("1234****5678"), eq("issuer-1")))
        .thenReturn("uc-1");
    when(performanceSnapshotStore.upsertAll(any()))
        .thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());

    SyncMyCardsResponse response =
        service.sync(USER_ID, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3));

    ArgumentCaptor<List<PerformanceSnapshotUpsert>> captor = ArgumentCaptor.forClass(List.class);
    verify(performanceSnapshotStore).upsertAll(captor.capture());
    List<PerformanceSnapshotUpsert> upserts = captor.getValue();
    assertEquals(1, upserts.size());
    assertEquals("2026-08", upserts.get(0).performanceMonth());
    assertEquals(300000, upserts.get(0).currentSpendAmount());
    assertEquals(1, response.getSyncedPerformanceCount());
  }

  @Test
  @DisplayName("실적조회 대상 월이 연동의 조회 가능 개월수를 벗어나면 실적조회 미지원 예외를 던져 전체 동기화를 중단한다")
  void throwsWhenTargetMonthExceedsLookback() {
    CodefConnection allowedConnection =
        new CodefConnection(
            "link-1", "cid-1", "0301", "issuer-1", "KB카드", 12, new byte[] {1, 2, 3}, false, false);
    CodefConnection blockedConnection =
        new CodefConnection(
            "link-2",
            "cid-2",
            "0302",
            "issuer-2",
            "신한카드",
            null,
            new byte[] {4, 5, 6},
            false,
            false);
    when(cardApprovalMapper.findUserCardsForMatching(USER_ID)).thenReturn(List.of(userCard()));
    when(codefCredentialMapper.findActiveConnectionsByUserId(USER_ID))
        .thenReturn(List.of(allowedConnection, blockedConnection));
    when(encryptor.decrypt(any())).thenReturn("900101");
    when(cardApprovalMapper.findExistingApprovalKeys(eq(USER_ID), any(), any()))
        .thenReturn(List.of());
    when(codefClient.getApprovals(
            anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(List.of());
    when(codefClient.getPerformance(
            eq("cid-1"), eq("0301"), eq("900101"), any(), any(), anyString()))
        .thenReturn(List.of());
    // lookback=12는 허용, lookback 미확인(null→0)은 차단되도록 오늘 기준 7개월 전을 조회 대상으로 삼는다.
    LocalDate sevenMonthsAgo =
        LocalDate.now(ZoneId.of("Asia/Seoul")).minusMonths(7).withDayOfMonth(1);

    PerformanceUnsupportedException exception =
        assertThrows(
            PerformanceUnsupportedException.class,
            () -> service.sync(USER_ID, sevenMonthsAgo, sevenMonthsAgo.plusDays(2)));

    assertTrue(exception.getMessage().contains("신한카드"));
    // 대상 월(7개월 전, lookback 12 이내)과 지난 달(8개월 전, lookback 12 이내) 모두 허용되어 두 번 조회한다.
    verify(codefClient, times(2))
        .getPerformance(eq("cid-1"), eq("0301"), eq("900101"), any(), any(), anyString());
    verify(codefClient, never())
        .getPerformance(eq("cid-2"), anyString(), anyString(), any(), any(), anyString());
    // 도중에 실패하면 이미 조회된 승인내역·실적도 커밋하지 않는다.
    verifyNoInteractions(approvalIngestStore, performanceSnapshotStore);
  }

  @Test
  @DisplayName("실적조회 가능 개월수가 -1인 연동은 실적조회 미지원 예외를 던져 전체 동기화를 중단한다")
  void throwsWhenLookbackIsMinusOne() {
    CodefConnection unsupportedConnection =
        new CodefConnection(
            "link-1", "cid-1", "0304", "issuer-1", "하나카드", -1, new byte[] {1, 2, 3}, false, false);
    when(cardApprovalMapper.findUserCardsForMatching(USER_ID)).thenReturn(List.of(userCard()));
    when(codefCredentialMapper.findActiveConnectionsByUserId(USER_ID))
        .thenReturn(List.of(unsupportedConnection));
    when(encryptor.decrypt(any())).thenReturn("900101");
    when(cardApprovalMapper.findExistingApprovalKeys(eq(USER_ID), any(), any()))
        .thenReturn(List.of());
    when(codefClient.getApprovals(
            anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(List.of());

    PerformanceUnsupportedException exception =
        assertThrows(
            PerformanceUnsupportedException.class,
            () -> service.sync(USER_ID, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3)));

    assertTrue(exception.getMessage().contains("하나카드"));
    verify(codefClient, never())
        .getPerformance(anyString(), anyString(), anyString(), any(), any(), anyString());
    // 실적조회 미지원으로 실패해도 승인내역 조회 자체는 그 연동에서 정상 수행된다.
    verify(codefClient)
        .getApprovals(
            eq("cid-1"), eq("0304"), eq("900101"), anyString(), anyString(), any(), any());
  }

  @Test
  @DisplayName("CODEF 승인내역 조회가 실패하면 ApprovalSyncFailedException으로 변환한다")
  void wrapsApprovalCodefFailure() {
    when(cardApprovalMapper.findUserCardsForMatching(USER_ID)).thenReturn(List.of(userCard()));
    when(codefCredentialMapper.findActiveConnectionsByUserId(USER_ID))
        .thenReturn(List.of(connection()));
    when(encryptor.decrypt(any())).thenReturn("900101");
    when(cardApprovalMapper.findExistingApprovalKeys(eq(USER_ID), any(), any()))
        .thenReturn(List.of());
    when(codefClient.getApprovals(
            anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
        .thenThrow(new CodefUnavailableException("CODEF 승인내역 조회에 실패했습니다."));

    ApprovalSyncFailedException exception =
        assertThrows(
            ApprovalSyncFailedException.class,
            () -> service.sync(USER_ID, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3)));

    assertEquals(CodefUnavailableException.class, exception.getCause().getClass());
    verifyNoInteractions(approvalIngestStore, performanceSnapshotStore);
  }

  @Test
  @DisplayName("CODEF 실적조회 호출 자체가 실패하면 PerformanceSyncFailedException으로 변환한다")
  void wrapsPerformanceCodefFailure() {
    when(cardApprovalMapper.findUserCardsForMatching(USER_ID)).thenReturn(List.of(userCard()));
    when(codefCredentialMapper.findActiveConnectionsByUserId(USER_ID))
        .thenReturn(List.of(connection()));
    when(encryptor.decrypt(any())).thenReturn("900101");
    when(cardApprovalMapper.findExistingApprovalKeys(eq(USER_ID), any(), any()))
        .thenReturn(List.of());
    when(codefClient.getApprovals(
            anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(List.of());
    when(codefClient.getPerformance(
            anyString(), anyString(), anyString(), any(), any(), anyString()))
        .thenThrow(new CodefUnavailableException("CODEF 실적조회에 실패했습니다."));

    PerformanceSyncFailedException exception =
        assertThrows(
            PerformanceSyncFailedException.class,
            () -> service.sync(USER_ID, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3)));

    assertEquals(CodefUnavailableException.class, exception.getCause().getClass());
    verifyNoInteractions(approvalIngestStore, performanceSnapshotStore);
  }

  @Test
  @DisplayName("비씨카드는 CODEF가 startDate 기준 전월 실적을 주므로 조회 대상 월보다 한 달 뒤를 보낸다")
  void requestsNextMonthForBcCard() {
    CodefConnection bcConnection =
        new CodefConnection(
            "link-1", "cid-1", "0305", "issuer-1", "비씨카드", 12, new byte[] {1, 2, 3}, false, false);
    when(cardApprovalMapper.findUserCardsForMatching(USER_ID)).thenReturn(List.of(userCard()));
    when(codefCredentialMapper.findActiveConnectionsByUserId(USER_ID))
        .thenReturn(List.of(bcConnection));
    when(encryptor.decrypt(any())).thenReturn("900101");
    when(cardApprovalMapper.findExistingApprovalKeys(eq(USER_ID), any(), any()))
        .thenReturn(List.of());
    when(codefClient.getApprovals(
            anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(List.of());
    when(approvalIngestStore.insertAll(any())).thenReturn(0);
    when(codefClient.getPerformance(
            anyString(), anyString(), anyString(), any(), any(), anyString()))
        .thenReturn(List.of());
    when(performanceSnapshotStore.upsertAll(any())).thenReturn(0);

    service.sync(USER_ID, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3));

    // 조회 대상 월은 2026-08이지만, 비씨카드는 익월(2026-09)을 startDate로 보내야 8월 실적을 받는다.
    verify(codefClient)
        .getPerformance(eq("cid-1"), eq("0305"), eq("900101"), any(), any(), eq("202609"));
  }

  @Test
  @DisplayName("카드번호가 필요한 카드사는 활성 카드마다 승인내역·실적조회를 개별 호출하고, " + "카드번호가 저장되지 않은 활성 카드는 건너뛴다")
  void ingestsPerCardWhenConnectionRequiresCardNo() {
    CodefConnection connection =
        new CodefConnection(
            "link-1", "cid-1", "0301", "issuer-1", "KB카드", 12, new byte[] {1, 2, 3}, true, true);
    when(cardApprovalMapper.findUserCardsForMatching(USER_ID)).thenReturn(List.of(userCard()));
    when(codefCredentialMapper.findActiveConnectionsByUserId(USER_ID))
        .thenReturn(List.of(connection));
    when(encryptor.decrypt(new byte[] {1, 2, 3})).thenReturn("900101");
    when(cardApprovalMapper.findExistingApprovalKeys(eq(USER_ID), any(), any()))
        .thenReturn(List.of());
    // 카드번호가 없는 활성 카드(활성화 검증이 정상 동작했다면 발생하지 않아야 하는 상태)는 로그만 남기고 건너뛴다.
    ActiveCardCredential withoutCardNo = new ActiveCardCredential("uc-missing", null, null);
    ActiveCardCredential withCardNo =
        new ActiveCardCredential("uc-1", new byte[] {9}, new byte[] {5});
    ActiveCardCredential withoutPassword = new ActiveCardCredential("uc-1", new byte[] {8}, null);
    when(cardApprovalMapper.findActiveCardCredentialsByCredentialId("link-1"))
        .thenReturn(List.of(withoutCardNo, withCardNo, withoutPassword));
    when(encryptor.decrypt(new byte[] {9})).thenReturn("9999888877776666");
    when(encryptor.decrypt(new byte[] {8})).thenReturn("8888777766665555");
    when(encryptor.decrypt(new byte[] {5})).thenReturn("5678");
    when(codefClient.getApprovals(
            eq("cid-1"),
            eq("0301"),
            eq("900101"),
            anyString(),
            anyString(),
            eq("9999888877776666"),
            eq("5678")))
        .thenReturn(List.of());
    when(codefClient.getApprovals(
            eq("cid-1"),
            eq("0301"),
            eq("900101"),
            anyString(),
            anyString(),
            eq("8888777766665555"),
            eq(null)))
        .thenReturn(List.of());
    when(codefClient.getPerformance(
            eq("cid-1"), eq("0301"), eq("900101"), eq("9999888877776666"), eq("5678"), anyString()))
        .thenReturn(List.of());
    when(codefClient.getPerformance(
            eq("cid-1"), eq("0301"), eq("900101"), eq("8888777766665555"), eq(null), anyString()))
        .thenReturn(List.of());
    when(approvalIngestStore.insertAll(any())).thenReturn(0);
    when(performanceSnapshotStore.upsertAll(any())).thenReturn(0);

    service.sync(USER_ID, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3));

    verify(codefClient)
        .getApprovals(
            eq("cid-1"),
            eq("0301"),
            eq("900101"),
            anyString(),
            anyString(),
            eq("9999888877776666"),
            eq("5678"));
    // 대상 월과 지난 달 실적을 각각 조회한다.
    verify(codefClient, times(2))
        .getPerformance(
            eq("cid-1"), eq("0301"), eq("900101"), eq("9999888877776666"), eq("5678"), anyString());
    // 연동 전체를 한 번에 조회하는 기존 방식(카드번호 없이)은 호출되지 않는다.
    verify(codefClient, never())
        .getApprovals(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            eq((String) null),
            eq((String) null));
  }

  @Test
  @DisplayName("institutionCode를 주면 그 기관코드 연동만 동기화한다")
  void syncsOnlyMatchingInstitutionWhenInstitutionCodeGiven() {
    CodefConnection kb = connection();
    CodefConnection shinhan = new CodefConnection(
        "link-2", "cid-2", "0302", "issuer-2", "신한카드", null, new byte[] {4, 5, 6}, false, false);
    when(cardApprovalMapper.findUserCardsForMatching(USER_ID)).thenReturn(List.of(userCard()));
    when(codefCredentialMapper.findActiveConnectionsByUserId(USER_ID))
        .thenReturn(List.of(kb, shinhan));
    when(encryptor.decrypt(any())).thenReturn("900101");
    when(cardApprovalMapper.findExistingApprovalKeys(eq(USER_ID), any(), any()))
        .thenReturn(List.of());
    when(codefClient.getApprovals(
            eq("cid"), eq("0301"), anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(List.of());
    when(approvalIngestStore.insertAll(any())).thenReturn(0);

    service.sync(USER_ID, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3), "0301");

    verify(codefClient)
        .getApprovals(eq("cid"), eq("0301"), anyString(), anyString(), anyString(), any(), any());
    verify(codefClient, never())
        .getApprovals(eq("cid-2"), anyString(), anyString(), anyString(), anyString(), any(), any());
  }

  @Test
  @DisplayName("institutionCode에 해당하는 활성 연동이 없으면 404 예외를 던진다")
  void throwsWhenInstitutionCodeNotFound() {
    when(cardApprovalMapper.findUserCardsForMatching(USER_ID)).thenReturn(List.of(userCard()));
    when(codefCredentialMapper.findActiveConnectionsByUserId(USER_ID))
        .thenReturn(List.of(connection()));

    assertThrows(
        CodefConnectionNotFoundException.class,
        () -> service.sync(
            USER_ID, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3), "9999"));
    verifyNoInteractions(codefClient);
  }

  private CodefApproval approval(
      String usedDate,
      String usedTime,
      String cardName,
      String store,
      String amount,
      String approvalNo,
      String homeForeign,
      String cancel) {
    return new CodefApproval(
        usedDate,
        usedTime,
        "1234****5678",
        cardName,
        store,
        amount,
        approvalNo,
        homeForeign,
        cancel,
        "{\"resMemberStoreName\":\"" + store + "\"}");
  }

  private CodefConnection connection() {
    return new CodefConnection(
        "link-1", "cid", "0301", "issuer-1", "KB카드", null, new byte[] {1, 2, 3}, false, false);
  }

  private UserCardMatchRow userCard() {
    return new UserCardMatchRow("uc-1", "issuer-1", "카드A", "1234****5678");
  }
}
