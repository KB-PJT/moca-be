package com.moca.mocabe.domain.codef.service;

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
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * POST /me/cards/sync에서 CODEF 승인내역·실적을 조회해 적재한다. 실적 조회 대상 월은 카드사가 지원하는 범위(issuers.
 * performance_lookback_months)를 벗어나면 {@link PerformanceUnsupportedException}, CODEF 호출 자체가 실패하면
 * {@link ApprovalSyncFailedException}/{@link PerformanceSyncFailedException}으로 구분해 던진다(하나라도 실패하면 전체
 * 실패 처리). 취소·부분취소·거절·해외결제는 완전히 제외하고 정상 국내 승인건만 적재하며, 중복은 (카드+승인번호) 또는
 * (카드+시각+금액+가맹점명)으로 걸러낸다.
 */
public class CardSyncService implements DisposableBean {

  private static final Logger LOGGER = Logger.getLogger(CardSyncService.class.getName());

  /** CODEF 호출(승인내역/실적, 카드 수 × 3개)을 동시에 몇 개까지 병행할지. 순차 호출 시 카드가 많으면 프론트 타임아웃(80초)을 넘겨서 도입했다. */
  private static final int CODEF_FETCH_PARALLELISM = 16;
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final DateTimeFormatter CODEF_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
  private static final DateTimeFormatter CODEF_MONTH = DateTimeFormatter.ofPattern("yyyyMM");
  private static final DateTimeFormatter PERFORMANCE_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

  /** performance_lookback_months가 NULL(정책 미확인)일 때의 기본값(이번 달까지만). */
  private static final int DEFAULT_PERFORMANCE_LOOKBACK_MONTHS = 0;

  /** 실적조회 자체를 지원하지 않는 카드사 표시값(0=당월만 지원과 구분). */
  private static final int PERFORMANCE_UNSUPPORTED_LOOKBACK_MONTHS = -1;

  /** 비씨카드는 CODEF가 startDate 기준 "전월" 실적을 줘서 +1개월 보정이 필요하다. */
  private static final String BC_CARD_INSTITUTION_CODE = "0305";

  private final CodefClient codefClient;
  private final CodefCredentialMapper codefCredentialMapper;
  private final CardApprovalMapper cardApprovalMapper;
  private final ApprovalCardMatcher approvalCardMatcher;
  private final MerchantLookup merchantLookup;
  private final ApprovalIngestStore approvalIngestStore;
  private final PerformanceSnapshotStore performanceSnapshotStore;
  private final Encryptor encryptor;
  private final BenefitUsageCalculationService benefitUsageCalculationService;
  private final ExecutorService codefFetchExecutor =
      Executors.newFixedThreadPool(CODEF_FETCH_PARALLELISM);

  public CardSyncService(
      CodefClient codefClient,
      CodefCredentialMapper codefCredentialMapper,
      CardApprovalMapper cardApprovalMapper,
      ApprovalCardMatcher approvalCardMatcher,
      MerchantLookup merchantLookup,
      ApprovalIngestStore approvalIngestStore,
      PerformanceSnapshotStore performanceSnapshotStore,
      Encryptor encryptor) {
    this(
        codefClient,
        codefCredentialMapper,
        cardApprovalMapper,
        approvalCardMatcher,
        merchantLookup,
        approvalIngestStore,
        performanceSnapshotStore,
        encryptor,
        BenefitUsageCalculationService.noop());
  }

  public CardSyncService(
      CodefClient codefClient,
      CodefCredentialMapper codefCredentialMapper,
      CardApprovalMapper cardApprovalMapper,
      ApprovalCardMatcher approvalCardMatcher,
      MerchantLookup merchantLookup,
      ApprovalIngestStore approvalIngestStore,
      PerformanceSnapshotStore performanceSnapshotStore,
      Encryptor encryptor,
      BenefitUsageCalculationService benefitUsageCalculationService) {
    this.codefClient = codefClient;
    this.codefCredentialMapper = codefCredentialMapper;
    this.cardApprovalMapper = cardApprovalMapper;
    this.approvalCardMatcher = approvalCardMatcher;
    this.merchantLookup = merchantLookup;
    this.approvalIngestStore = approvalIngestStore;
    this.performanceSnapshotStore = performanceSnapshotStore;
    this.encryptor = encryptor;
    this.benefitUsageCalculationService = benefitUsageCalculationService;
  }

  @Override
  public void destroy() {
    codefFetchExecutor.shutdown();
  }

  public SyncMyCardsResponse sync(String userId, LocalDate startDate, LocalDate endDate) {
    return sync(userId, startDate, endDate, null);
  }

  /**
   * startDate/endDate가 null이면 이번 달 1일~오늘로 채운다. 계산 실패 시 재동기화에서 영구 누락되지 않도록 전체를 하나의
   * 트랜잭션으로 롤백한다. institutionCode를 주면 그 카드사 연동만 동기화하며, 연동이 없으면
   * {@link CodefConnectionNotFoundException}(404)을 던진다.
   */
  @Transactional
  public SyncMyCardsResponse sync(
      String userId, LocalDate startDate, LocalDate endDate, String institutionCode) {
    LocalDate today = LocalDate.now(KST);
    LocalDate from = startDate != null ? startDate : today.withDayOfMonth(1);
    LocalDate to = endDate != null ? endDate : today;
    if (from.isAfter(to)) {
      throw new InvalidSyncPeriodException("조회 시작일이 종료일보다 늦을 수 없습니다.");
    }

    List<UserCardMatchRow> userCards = cardApprovalMapper.findUserCardsForMatching(userId);
    List<CodefConnection> connections = codefCredentialMapper.findActiveConnectionsByUserId(userId);
    if (institutionCode != null) {
      connections = connections.stream()
          .filter(connection -> institutionCode.equals(connection.institutionCode()))
          .toList();
      if (connections.isEmpty()) {
        throw new CodefConnectionNotFoundException(institutionCode);
      }
    }

    IngestResult result = new IngestResult(0, 0);
    if (!userCards.isEmpty() && !connections.isEmpty()) {
      result = ingest(userId, userCards, connections, from, to);
    }
    return new SyncMyCardsResponse(
        userCards.size(), result.approvalCount(), result.performanceCount(), formatSyncedAt());
  }

  /** 승인내역 적재 건수와 실적 스냅샷 upsert 건수다. */
  private record IngestResult(int approvalCount, int performanceCount) { }

  private IngestResult ingest(
      String userId,
      List<UserCardMatchRow> userCards,
      List<CodefConnection> connections,
      LocalDate from,
      LocalDate to) {
    LocalDateTime fromUtc =
        from.atStartOfDay(KST).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    LocalDateTime toUtc =
        to.plusDays(1).atStartOfDay(KST).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    Set<String> seenKeys = new HashSet<>();
    for (ExistingApprovalKey key :
        cardApprovalMapper.findExistingApprovalKeys(userId, fromUtc, toUtc)) {
      seenKeys.add(
          dedupeKey(
              key.userCardId(),
              key.approvalNumber(),
              key.approvedAt(),
              key.amount(),
              key.merchantName()));
    }

    String startStr = from.format(CODEF_DATE);
    String endStr = to.format(CODEF_DATE);
    for (UserCardMatchRow card : userCards) {
      LOGGER.fine("동기화 대상 보유카드: name='" + card.cardName() + "' cardNo='" + card.cardNo() + "'");
    }
    // 가맹점 후보는 승인건마다 다시 읽으면 비용이 커지므로 회차당 한 번만 로드한다.
    MerchantCandidateSnapshot merchantCandidates = merchantLookup.loadCandidates();
    IngestStats stats = new IngestStats();
    List<ApprovalInsert> inserts = new ArrayList<>();
    List<PerformanceSnapshotUpsert> performanceUpserts = new ArrayList<>();
    // 실적 조회 대상 월은 from이 속한 달이며, 지난 달 실적도 가능하면 함께 적재한다(미지원이면 그 달만 건너뜀).
    YearMonth targetMonth = YearMonth.from(from);
    YearMonth previousMonth = targetMonth.minusMonths(1);
    YearMonth currentMonth = YearMonth.now(KST);
    long monthsBack = Math.max(0, ChronoUnit.MONTHS.between(targetMonth, currentMonth));
    long previousMonthsBack = Math.max(0, ChronoUnit.MONTHS.between(previousMonth, currentMonth));
    String performanceMonth = targetMonth.format(PERFORMANCE_MONTH);
    String previousPerformanceMonth = previousMonth.format(PERFORMANCE_MONTH);

    // CODEF 호출은 스레드풀에서 병행하고, DB 조회와 결과 반영은 메인 스레드에서만 한다(트랜잭션 커넥션이
    // 스레드로컬에 묶여 있어 다른 스레드에서 매퍼를 부르면 트랜잭션 밖에서 별도 커넥션이 열린다).
    // connectedId(=CODEF 계정 세션)가 같은 호출끼리는 동시에 보내면 CODEF가 거부할 수 있어(세션 하나를
    // 여러 요청이 동시에 쓰는 셈), connectedId별로 순서를 유지하고 서로 다른 connectedId끼리만 병행한다.
    long fetchStartMs = System.currentTimeMillis();
    Map<String, CompletableFuture<?>> connectedIdChain = new HashMap<>();
    List<FetchTask> fetchTasks = new ArrayList<>();
    for (CodefConnection connection : connections) {
      String birthDate = encryptor.decrypt(connection.birthDateEnc());
      if (connection.requiresCardNo()) {
        // 카드번호가 필요한 카드사는 카드마다 카드번호가 달라 카드 단위로 개별 호출한다.
        for (ActiveCardCredential cardCredential :
            cardApprovalMapper.findActiveCardCredentialsByCredentialId(
                connection.codefAccountCredentialId())) {
          if (cardCredential.cardNumberEnc() == null) {
            LOGGER.warning(
                "활성 카드에 카드번호가 없어 동기화에서 건너뜁니다. userCardId=" + cardCredential.userCardId());
            continue;
          }
          String cardNo = encryptor.decrypt(cardCredential.cardNumberEnc());
          String cardPassword =
              cardCredential.cardPasswordEnc() == null
                  ? null
                  : encryptor.decrypt(cardCredential.cardPasswordEnc());
          fetchTasks.add(
              submitFetch(
                  connectedIdChain,
                  connection,
                  birthDate,
                  startStr,
                  endStr,
                  cardNo,
                  cardPassword,
                  targetMonth,
                  monthsBack,
                  performanceMonth,
                  previousMonth,
                  previousMonthsBack,
                  previousPerformanceMonth));
        }
      } else if (!cardApprovalMapper
          .findActiveCardCredentialsByCredentialId(connection.codefAccountCredentialId())
          .isEmpty()) {
        fetchTasks.add(
            submitFetch(
                connectedIdChain,
                connection,
                birthDate,
                startStr,
                endStr,
                null,
                null,
                targetMonth,
                monthsBack,
                performanceMonth,
                previousMonth,
                previousMonthsBack,
                previousPerformanceMonth));
      } else {
        LOGGER.fine(
            "매칭된 활성 카드가 없어 연동 동기화를 건너뜁니다. credentialId="
                + connection.codefAccountCredentialId());
      }
    }

    // seenKeys 등 공유 상태를 건드리는 매칭·중복제거는 결과가 온 순서대로 메인 스레드에서 처리한다.
    for (FetchTask task : fetchTasks) {
      List<CodefApproval> approvals = joinUnwrapped(task.approvalsFuture());
      List<CodefCardPerformance> performances = joinUnwrapped(task.performancesFuture());
      List<CodefCardPerformance> previousPerformances = joinUnwrapped(task.previousPerformancesFuture());
      applyFetchResult(
          userId,
          userCards,
          task.connection(),
          approvals,
          performances,
          previousPerformances,
          performanceMonth,
          previousPerformanceMonth,
          merchantCandidates,
          seenKeys,
          stats,
          inserts,
          performanceUpserts);
    }
    long fetchElapsedMs = System.currentTimeMillis() - fetchStartMs;

    int inserted;
    if (benefitUsageCalculationService.isEnabled()) {
      List<ApprovalInsert> insertedApprovals =
          approvalIngestStore.insertAllReturningInserted(inserts);
      inserted = insertedApprovals.size();
      benefitUsageCalculationService.calculateAndPersist(insertedApprovals);
    } else {
      inserted = approvalIngestStore.insertAll(inserts);
    }
    int upsertedPerformances = performanceSnapshotStore.upsertAll(performanceUpserts);
    LOGGER.info(
        String.format(
            "승인내역 동기화 결과 period=%s~%s units=%d connectedIds=%d codefFetchMs=%d fetched=%d"
                + " filtered=%d unmatched=%d invalid=%d duplicate=%d inserted=%d",
            startStr,
            endStr,
            fetchTasks.size(),
            connectedIdChain.size(),
            fetchElapsedMs,
            stats.fetched,
            stats.filtered,
            stats.unmatched,
            stats.invalid,
            stats.duplicate,
            inserted));
    return new IngestResult(inserted, upsertedPerformances);
  }

  /**
   * 연동(또는 카드 한 장) 단위로 붙는 승인내역/당월실적/전월실적 CODEF 호출 3개의 future다. 카드 사이뿐 아니라
   * 이 3개도 서로 독립적이라 별도 작업으로 풀어 동시에 던진다(같은 풀에서 안쪽 작업을 기다리는 중첩 제출이
   * 아니라 메인 스레드가 셋 다 직접 제출하므로 스레드풀 고갈 데드락 위험이 없다).
   */
  private record FetchTask(
      CodefConnection connection,
      CompletableFuture<List<CodefApproval>> approvalsFuture,
      CompletableFuture<List<CodefCardPerformance>> performancesFuture,
      CompletableFuture<List<CodefCardPerformance>> previousPerformancesFuture) { }

  /**
   * 연동(또는 카드 한 장) 단위 CODEF 호출 3개를 제출한다. connectedId가 다른 호출끼리는 스레드풀에서 병행하고,
   * 같은 connectedId(=같은 CODEF 계정 세션)를 쓰는 호출끼리는 connectedIdChain으로 순서를 강제해 겹치지 않게 한다.
   */
  private FetchTask submitFetch(
      Map<String, CompletableFuture<?>> connectedIdChain,
      CodefConnection connection,
      String birthDate,
      String startStr,
      String endStr,
      String cardNo,
      String cardPassword,
      YearMonth targetMonth,
      long monthsBack,
      String performanceMonth,
      YearMonth previousMonth,
      long previousMonthsBack,
      String previousPerformanceMonth) {
    CompletableFuture<List<CodefApproval>> approvalsFuture =
        chainedSupply(
            connectedIdChain,
            connection.connectedId(),
            () -> fetchApprovals(connection, birthDate, startStr, endStr, cardNo, cardPassword));
    CompletableFuture<List<CodefCardPerformance>> performancesFuture =
        chainedSupply(
            connectedIdChain,
            connection.connectedId(),
            () -> fetchPerformances(
                connection, birthDate, targetMonth, monthsBack, performanceMonth, cardNo, cardPassword));
    CompletableFuture<List<CodefCardPerformance>> previousPerformancesFuture =
        chainedSupply(
            connectedIdChain,
            connection.connectedId(),
            () -> fetchPreviousPerformances(
                connection, birthDate, previousMonth, previousMonthsBack,
                previousPerformanceMonth, cardNo, cardPassword));
    return new FetchTask(connection, approvalsFuture, performancesFuture, previousPerformancesFuture);
  }

  /**
   * connectedId별로 마지막에 예약된 작업 뒤에 이어붙여, 같은 connectedId 호출은 항상 하나씩 순서대로
   * 실행되게 한다(스레드풀 자체는 여러 connectedId를 동시에 처리하므로 다른 계정끼리는 그대로 병행된다).
   */
  private <T> CompletableFuture<T> chainedSupply(
      Map<String, CompletableFuture<?>> connectedIdChain, String connectedId, Supplier<T> supplier) {
    CompletableFuture<?> previous =
        connectedIdChain.getOrDefault(connectedId, CompletableFuture.completedFuture(null));
    CompletableFuture<T> next = previous.thenApplyAsync(ignored -> supplier.get(), codefFetchExecutor);
    connectedIdChain.put(connectedId, next);
    return next;
  }

  /**
   * future가 던진 예외를 CompletionException 포장 없이 원래 타입 그대로 다시 던진다. fetchApprovals/
   * fetchPerformances/fetchPreviousPerformances는 항상 RuntimeException만 던지므로 cause도 항상 그렇다.
   */
  private <T> T joinUnwrapped(CompletableFuture<T> future) {
    try {
      return future.join();
    } catch (CompletionException exception) {
      throw (RuntimeException) exception.getCause();
    }
  }

  /** CODEF 원본 응답을 매칭·중복제거해 inserts/performanceUpserts에 누적한다(메인 스레드 순차 호출). */
  private void applyFetchResult(
      String userId,
      List<UserCardMatchRow> userCards,
      CodefConnection connection,
      List<CodefApproval> approvals,
      List<CodefCardPerformance> performances,
      List<CodefCardPerformance> previousPerformances,
      String performanceMonth,
      String previousPerformanceMonth,
      MerchantCandidateSnapshot merchantCandidates,
      Set<String> seenKeys,
      IngestStats stats,
      List<ApprovalInsert> inserts,
      List<PerformanceSnapshotUpsert> performanceUpserts) {
    for (CodefApproval approval : approvals) {
      ApprovalInsert insert =
          toInsert(
              userId,
              userCards,
              approval,
              connection.issuerId(),
              merchantCandidates,
              seenKeys,
              stats);
      if (insert != null) {
        inserts.add(insert);
      }
    }

    for (CodefCardPerformance performance : performances) {
      PerformanceSnapshotUpsert upsert =
          toPerformanceUpsert(userCards, performance, connection.issuerId(), performanceMonth);
      if (upsert != null) {
        performanceUpserts.add(upsert);
      }
    }

    for (CodefCardPerformance performance : previousPerformances) {
      PerformanceSnapshotUpsert upsert =
          toPerformanceUpsert(
              userCards, performance, connection.issuerId(), previousPerformanceMonth);
      if (upsert != null) {
        performanceUpserts.add(upsert);
      }
    }
  }

  /** 지난 달 실적은 부가 정보라 미지원/실패해도 그 달만 건너뛰고 대상 월 동기화는 살린다. */
  private List<CodefCardPerformance> fetchPreviousPerformances(
      CodefConnection connection,
      String birthDate,
      YearMonth previousMonth,
      long previousMonthsBack,
      String previousPerformanceMonth,
      String cardNo,
      String cardPassword) {
    try {
      return fetchPerformances(
          connection,
          birthDate,
          previousMonth,
          previousMonthsBack,
          previousPerformanceMonth,
          cardNo,
          cardPassword);
    } catch (PerformanceUnsupportedException | PerformanceSyncFailedException exception) {
      LOGGER.fine(
          "지난 달 실적조회를 건너뜁니다(issuerId="
              + connection.issuerId()
              + ", month="
              + previousPerformanceMonth
              + "). "
              + exception.getMessage());
      return List.of();
    }
  }

  private List<CodefApproval> fetchApprovals(
      CodefConnection connection,
      String birthDate,
      String startStr,
      String endStr,
      String cardNo,
      String cardPassword) {
    try {
      return codefClient.getApprovals(
          connection.connectedId(),
          connection.institutionCode(),
          birthDate,
          startStr,
          endStr,
          cardNo,
          cardPassword);
    } catch (CodefUnavailableException exception) {
      throw new ApprovalSyncFailedException(
          "승인내역 동기화에 실패했습니다(issuerId=" + connection.issuerId() + "). " + "잠시 후 다시 시도해주세요.",
          exception);
    }
  }

  /** 조회 가능 범위를 벗어나면 영구 실패(PerformanceUnsupportedException), CODEF 호출 자체가 실패하면 일시 실패로 구분한다. */
  private List<CodefCardPerformance> fetchPerformances(
      CodefConnection connection,
      String birthDate,
      YearMonth targetMonth,
      long monthsBack,
      String performanceMonth,
      String cardNo,
      String cardPassword) {
    int allowedLookback =
        connection.performanceLookbackMonths() != null
            ? connection.performanceLookbackMonths()
            : DEFAULT_PERFORMANCE_LOOKBACK_MONTHS;
    if (allowedLookback == PERFORMANCE_UNSUPPORTED_LOOKBACK_MONTHS) {
      throw new PerformanceUnsupportedException(
          connection.issuerName() + "는 실적조회를 지원하지 않는 카드사입니다.");
    }
    if (monthsBack > allowedLookback) {
      throw new PerformanceUnsupportedException(
          connection.issuerName()
              + "는 "
              + performanceMonth
              + " 실적을 조회할 수 없습니다(조회 가능 범위: 최근 "
              + allowedLookback
              + "개월).");
    }
    String performanceStartDate =
        resolvePerformanceStartDate(targetMonth, connection.institutionCode());
    try {
      return codefClient.getPerformance(
          connection.connectedId(),
          connection.institutionCode(),
          birthDate,
          cardNo,
          cardPassword,
          performanceStartDate);
    } catch (CodefUnavailableException exception) {
      throw new PerformanceSyncFailedException(
          "실적조회 동기화에 실패했습니다(issuerId=" + connection.issuerId() + "). " + "잠시 후 다시 시도해주세요.",
          exception);
    }
  }

  private String resolvePerformanceStartDate(YearMonth targetMonth, String institutionCode) {
    YearMonth requestMonth =
        BC_CARD_INSTITUTION_CODE.equals(institutionCode) ? targetMonth.plusMonths(1) : targetMonth;
    return requestMonth.format(CODEF_MONTH);
  }

  /** currentSpendAmount가 없거나 보유카드와 매칭되지 않으면 null(upsert 대상 제외). */
  private PerformanceSnapshotUpsert toPerformanceUpsert(
      List<UserCardMatchRow> userCards,
      CodefCardPerformance performance,
      String issuerId,
      String performanceMonth) {
    if (performance.currentSpendAmount() == null) {
      return null;
    }
    String userCardId =
        approvalCardMatcher.match(
            userCards, performance.cardName(), performance.cardNo(), issuerId);
    if (userCardId == null) {
      LOGGER.fine(
          "미매칭 실적: resCardName='"
              + performance.cardName()
              + "' resCardNo='"
              + performance.cardNo()
              + "'");
      return null;
    }
    return new PerformanceSnapshotUpsert(
        UUID.randomUUID().toString(),
        userCardId,
        performanceMonth,
        performance.currentSpendAmount());
  }

  private ApprovalInsert toInsert(
      String userId,
      List<UserCardMatchRow> userCards,
      CodefApproval approval,
      String issuerId,
      MerchantCandidateSnapshot merchantCandidates,
      Set<String> seenKeys,
      IngestStats stats) {
    stats.fetched++;
    if (!approval.isNormalApproval() || !approval.isDomestic()) {
      stats.filtered++;
      return null;
    }
    String userCardId = approvalCardMatcher.match(userCards, approval, issuerId);
    if (userCardId == null) {
      stats.unmatched++;
      LOGGER.fine(
          "미매칭 승인: resCardName='"
              + approval.cardName()
              + "' resCardNo='"
              + approval.cardNo()
              + "'");
      return null;
    }
    LocalDateTime approvedAt = toApprovedAtUtc(approval.usedDate(), approval.usedTime());
    Integer amount = parseAmount(approval.usedAmount());
    if (approvedAt == null || amount == null) {
      stats.invalid++;
      return null;
    }
    String dedupeKey =
        dedupeKey(
            userCardId, approval.approvalNo(), approvedAt, amount, approval.memberStoreName());
    if (!seenKeys.add(dedupeKey)) {
      stats.duplicate++;
      return null;
    }
    String merchantId = merchantCandidates.resolveMerchantId(approval.memberStoreName());
    return new ApprovalInsert(
        UUID.randomUUID().toString(),
        userId,
        userCardId,
        merchantId,
        approval.approvalNo(),
        approvedAt,
        approval.memberStoreName(),
        amount,
        approval.sourcePayload());
  }

  /** 적재 과정에서 드랍된 사유별 집계(진단 로그용). */
  private static final class IngestStats {
    private int fetched;
    private int filtered;
    private int unmatched;
    private int invalid;
    private int duplicate;
  }

  /** 승인번호가 있으면 (카드+승인번호), 없으면 (카드+시각+금액+가맹점명)으로 중복 키를 만든다. */
  private String dedupeKey(
      String userCardId,
      String approvalNumber,
      LocalDateTime approvedAt,
      int amount,
      String merchantName) {
    if (approvalNumber != null && !approvalNumber.isBlank()) {
      return userCardId + "A" + approvalNumber;
    }
    return userCardId + "B" + approvedAt + "" + amount + "" + merchantName;
  }

  private LocalDateTime toApprovedAtUtc(String usedDate, String usedTime) {
    if (usedDate == null || usedDate.length() != 8) {
      return null;
    }
    try {
      LocalDate date = LocalDate.parse(usedDate, CODEF_DATE);
      LocalTime time = parseTime(usedTime);
      return date.atTime(time).atZone(KST).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    } catch (RuntimeException exception) {
      LOGGER.fine("승인 시각 파싱에 실패해 건너뜁니다. usedDate=" + usedDate);
      return null;
    }
  }

  private LocalTime parseTime(String usedTime) {
    if (usedTime == null || usedTime.isBlank()) {
      return LocalTime.MIDNIGHT;
    }
    String padded = (usedTime + "000000").substring(0, 6);
    int hour = Integer.parseInt(padded.substring(0, 2));
    int minute = Integer.parseInt(padded.substring(2, 4));
    int second = Integer.parseInt(padded.substring(4, 6));
    return LocalTime.of(hour, minute, second);
  }

  private Integer parseAmount(String usedAmount) {
    if (usedAmount == null) {
      return null;
    }
    String digits = usedAmount.replaceAll("[^0-9-]", "");
    if (digits.isEmpty() || "-".equals(digits)) {
      return null;
    }
    try {
      return Integer.valueOf(digits);
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private String formatSyncedAt() {
    return OffsetDateTime.now(KST).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  }
}
