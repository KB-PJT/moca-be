package com.moca.mocabe.domain.codef.service;

import com.moca.mocabe.domain.benefit.service.BenefitUsageCalculationService;
import com.moca.mocabe.domain.card.dto.SyncMyCardsResponse;
import com.moca.mocabe.domain.codef.exception.ApprovalSyncFailedException;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.springframework.transaction.annotation.Transactional;

/**
 * POST /me/cards/sync에서 CODEF 승인내역을 조회해 새 건만 card_payment_approvals에 적재하고, 실적현황을 조회해
 * user_card_performance_snapshots에 upsert한다. 실적 조회 대상 월은 sync의 startDate가 속한 달이며(기본값은 이번 달), 그 달이
 * 카드사가 지원하는 조회 가능 범위 (issuers.performance_lookback_months, NULL이면 이번 달까지만)를 벗어나거나 카드사가 실적조회 자체를
 * 지원하지 않으면(-1) 재시도해도 항상 실패하는 영구 조건이므로 {@link PerformanceUnsupportedException}(400)을 던져 동기화 전체를
 * 실패시킨다. CODEF 실적조회 호출 자체가 실패하는 일시적 상황은 {@link PerformanceSyncFailedException}(503)으로 구분한다. 승인내역 조회
 * 실패는 {@link ApprovalSyncFailedException}으로 구분해 응답 code를 다르게 내려보낸다 (사용자 결정: 부분 성공 대신 하나라도 실패하면 무엇이
 * 문제인지 구분해서 전체를 실패로 처리). 비씨카드(0305)는 CODEF가 startDate 기준 "전월" 실적을 주는 카드사라 조회 대상 월보다 한 달 뒤를
 * startDate로 보내야 하며, 이 보정은 이 카드사에만 적용한다.
 *
 * 대상 월의 실적과 더불어 바로 전 달(대상 월 - 1개월) 실적도 가능하면 함께 조회해 적재한다. 다만 지난 달 실적은 부가 정보이므로 카드사가 그만큼 과거를
 * 지원하지 않거나(PerformanceUnsupportedException) CODEF 호출이 실패해도(PerformanceSyncFailedException) 대상 월 동기화
 * 전체를 실패시키지 않고 지난 달분만 건너뛴다.
 *
 * 취소·부분취소·거절 및 해외결제는 반전 처리하지 않고 완전히 제외한다. 따라서 이 항목들은 승인 적재, 혜택 계산, 미적용 혜택 집계 어느 단계에도 들어가지 않으며 정상
 * 국내 승인건만 적재한다. 카드 매칭은 {@link ApprovalCardMatcher}, 가맹점 매칭은 {@link MerchantLookup}에 위임하며, 이미 적재된 건은
 * (카드+승인번호) 또는 (카드+시각+금액+가맹점명)으로 중복을 걸러낸다.
 */
public class CardSyncService {

  private static final Logger LOGGER = Logger.getLogger(CardSyncService.class.getName());
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final DateTimeFormatter CODEF_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
  private static final DateTimeFormatter CODEF_MONTH = DateTimeFormatter.ofPattern("yyyyMM");
  private static final DateTimeFormatter PERFORMANCE_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

  /** issuers.performance_lookback_months가 NULL(정책 미확인)일 때 보수적으로 적용하는 기본값(이번 달까지만). */
  private static final int DEFAULT_PERFORMANCE_LOOKBACK_MONTHS = 0;

  /** 이 값이면 실적조회 자체를 지원하지 않는 카드사로 확인된 것이다(0=당월만 지원과 구분). */
  private static final int PERFORMANCE_UNSUPPORTED_LOOKBACK_MONTHS = -1;

  /** 비씨카드 기관코드. CODEF가 startDate 기준 "전월" 실적을 주므로 +1개월 보정이 필요하다. */
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

  /**
   * startDate/endDate는 KST 기준이며, null이면 이번 달 1일~오늘로 기본값을 채운다. 승인 적재·혜택 계산·실적 스냅샷은 하나의 트랜잭션으로 확정한다.
   * 계산이 실패하면 승인만 남아 재동기화에서 영구적으로 계산이 누락되는 상태를 막기 위해 전체를 롤백한다.
   */
  @Transactional
  public SyncMyCardsResponse sync(String userId, LocalDate startDate, LocalDate endDate) {
    LocalDate today = LocalDate.now(KST);
    LocalDate from = startDate != null ? startDate : today.withDayOfMonth(1);
    LocalDate to = endDate != null ? endDate : today;
    if (from.isAfter(to)) {
      throw new InvalidSyncPeriodException("조회 시작일이 종료일보다 늦을 수 없습니다.");
    }

    List<UserCardMatchRow> userCards = cardApprovalMapper.findUserCardsForMatching(userId);
    List<CodefConnection> connections = codefCredentialMapper.findActiveConnectionsByUserId(userId);

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
    // 카드 매칭 실패 원인을 진단할 수 있도록 매칭 후보(보유카드)의 이름·카드번호를 남긴다(FINE).
    for (UserCardMatchRow card : userCards) {
      LOGGER.fine("동기화 대상 보유카드: name='" + card.cardName() + "' cardNo='" + card.cardNo() + "'");
    }
    // 가맹점 후보를 승인건마다 다시 조회하면 비용이 승인건 수만큼 반복되므로 이 회차 시작 시 한 번만 읽는다.
    MerchantCandidateSnapshot merchantCandidates = merchantLookup.loadCandidates();
    IngestStats stats = new IngestStats();
    List<ApprovalInsert> inserts = new ArrayList<>();
    List<PerformanceSnapshotUpsert> performanceUpserts = new ArrayList<>();
    // 실적 조회 대상 월은 승인내역 조회 시작일(from)이 속한 달로 삼는다(기본값 이번 달 1일 → 이번 달).
    // 지난 달 실적도 가능하면 함께 적재한다(카드사가 지원하지 않으면 대상 월 동기화는 살리고 지난 달만 건너뛴다).
    YearMonth targetMonth = YearMonth.from(from);
    YearMonth previousMonth = targetMonth.minusMonths(1);
    YearMonth currentMonth = YearMonth.now(KST);
    long monthsBack = Math.max(0, ChronoUnit.MONTHS.between(targetMonth, currentMonth));
    long previousMonthsBack = Math.max(0, ChronoUnit.MONTHS.between(previousMonth, currentMonth));
    String performanceMonth = targetMonth.format(PERFORMANCE_MONTH);
    String previousPerformanceMonth = previousMonth.format(PERFORMANCE_MONTH);
    for (CodefConnection connection : connections) {
      String birthDate = encryptor.decrypt(connection.birthDateEnc());
      if (connection.requiresCardNo()) {
        // 카드번호가 필요한 카드사는 카드마다 카드번호가 달라 연동 전체를 한 번에 조회할 수 없으므로,
        // 활성 카드별로 저장된 카드번호/비밀번호를 꺼내 승인내역·실적조회를 각각 호출한다.
        for (ActiveCardCredential cardCredential :
            cardApprovalMapper.findActiveCardCredentialsByCredentialId(
                connection.codefAccountCredentialId())) {
          if (cardCredential.cardNumberEnc() == null) {
            // 활성화 검증(activateCards)이 정상 동작했다면 발생하지 않아야 하는 상태다.
            LOGGER.warning(
                "활성 카드에 카드번호가 없어 동기화에서 건너뜁니다. userCardId=" + cardCredential.userCardId());
            continue;
          }
          String cardNo = encryptor.decrypt(cardCredential.cardNumberEnc());
          // 카드사가 카드번호만 요구하고 카드 비밀번호는 요구하지 않으면 정상적으로 null이다.
          String cardPassword =
              cardCredential.cardPasswordEnc() == null
                  ? null
                  : encryptor.decrypt(cardCredential.cardPasswordEnc());
          fetchAndCollect(
              userId,
              userCards,
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
              previousPerformanceMonth,
              merchantCandidates,
              seenKeys,
              stats,
              inserts,
              performanceUpserts);
        }
      } else {
        fetchAndCollect(
            userId,
            userCards,
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
            previousPerformanceMonth,
            merchantCandidates,
            seenKeys,
            stats,
            inserts,
            performanceUpserts);
      }
    }
    int inserted;
    if (benefitUsageCalculationService.isEnabled()) {
      List<ApprovalInsert> insertedApprovals =
          approvalIngestStore.insertAllReturningInserted(inserts);
      inserted = insertedApprovals.size();
      benefitUsageCalculationService.calculateAndPersist(insertedApprovals);
    } else {
      // 기존 단위 테스트와 혜택 계산을 구성하지 않은 실행 환경의 동기화 계약을 그대로 보존한다.
      inserted = approvalIngestStore.insertAll(inserts);
    }
    int upsertedPerformances = performanceSnapshotStore.upsertAll(performanceUpserts);
    // 승인내역이 왜 적재되지 않는지 진단할 수 있도록 드랍 사유별 집계를 한 줄로 남긴다.
    LOGGER.info(
        String.format(
            "승인내역 동기화 결과 period=%s~%s fetched=%d filtered=%d unmatched=%d invalid=%d duplicate=%d"
                + " inserted=%d",
            startStr,
            endStr,
            stats.fetched,
            stats.filtered,
            stats.unmatched,
            stats.invalid,
            stats.duplicate,
            inserted));
    return new IngestResult(inserted, upsertedPerformances);
  }

  /**
   * 연동(또는 카드번호가 필요한 카드사면 카드 한 장) 단위로 승인내역·실적을 조회해 inserts/ performanceUpserts에 누적한다.
   * cardNo/cardPassword는 카드번호가 필요하지 않은 카드사면 null이다.
   */
  private void fetchAndCollect(
      String userId,
      List<UserCardMatchRow> userCards,
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
      String previousPerformanceMonth,
      MerchantCandidateSnapshot merchantCandidates,
      Set<String> seenKeys,
      IngestStats stats,
      List<ApprovalInsert> inserts,
      List<PerformanceSnapshotUpsert> performanceUpserts) {
    List<CodefApproval> approvals =
        fetchApprovals(connection, birthDate, startStr, endStr, cardNo, cardPassword);
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

    List<CodefCardPerformance> performances =
        fetchPerformances(
            connection, birthDate, targetMonth, monthsBack, performanceMonth, cardNo, cardPassword);
    for (CodefCardPerformance performance : performances) {
      PerformanceSnapshotUpsert upsert =
          toPerformanceUpsert(userCards, performance, connection.issuerId(), performanceMonth);
      if (upsert != null) {
        performanceUpserts.add(upsert);
      }
    }

    List<CodefCardPerformance> previousPerformances =
        fetchPreviousPerformances(
            connection,
            birthDate,
            previousMonth,
            previousMonthsBack,
            previousPerformanceMonth,
            cardNo,
            cardPassword);
    for (CodefCardPerformance performance : previousPerformances) {
      PerformanceSnapshotUpsert upsert =
          toPerformanceUpsert(
              userCards, performance, connection.issuerId(), previousPerformanceMonth);
      if (upsert != null) {
        performanceUpserts.add(upsert);
      }
    }
  }

  /**
   * 지난 달 실적은 "가능하면" 함께 적재하는 부가 정보라, 조회 가능 범위를 벗어나거나(PerformanceUnsupportedException)
   * CODEF 호출이 실패해도(PerformanceSyncFailedException) 대상 월 동기화 전체를 실패시키지 않고 지난 달만 건너뛴다.
   */
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

  /** CODEF 승인내역 조회가 실패하면 원인을 구분할 수 있도록 ApprovalSyncFailedException으로 감싼다. */
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

  /**
   * 이 연동으로 조회 대상 월(targetMonth)의 실적을 받을 수 있는지 먼저 확인하고(카드사 미지원 또는 조회 가능 범위 초과면 재시도해도 항상 실패하는 영구
   * 조건이므로 PerformanceUnsupportedException), 가능하면 CODEF를 호출한다. CODEF 호출 자체가 실패하면 일시적 상황이므로 별도로
   * PerformanceSyncFailedException으로 감싸 승인내역 실패와 응답 code로 구분되게 한다. cardNo/cardPassword는 KB
   * 카드소지확인·현대카드 아이디로그인처럼 일부 카드사만 요구하는 값으로, 요구하지 않으면 null이다.
   */
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

  /**
   * 실적조회 CODEF 요청의 startDate(YYYYMM)를 계산한다. 비씨카드(0305)는 CODEF가 startDate 기준 "전월" 실적을 응답하는 카드사라, 조회
   * 대상 월(targetMonth)의 실적을 받으려면 startDate를 한 달 뒤로 보내야 한다(당월 실적 조회는 익월로 설정). 그 외 카드사는 targetMonth를
   * 그대로 보낸다.
   */
  private String resolvePerformanceStartDate(YearMonth targetMonth, String institutionCode) {
    YearMonth requestMonth =
        BC_CARD_INSTITUTION_CODE.equals(institutionCode) ? targetMonth.plusMonths(1) : targetMonth;
    return requestMonth.format(CODEF_MONTH);
  }

  /** currentSpendAmount가 없거나(혜택 없음) 보유카드와 매칭되지 않으면 null을 반환해 upsert 대상에서 뺀다. */
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
    // 취소/부분취소/거절·해외결제는 적재하지 않는다.
    if (!approval.isNormalApproval() || !approval.isDomestic()) {
      stats.filtered++;
      return null;
    }
    String userCardId = approvalCardMatcher.match(userCards, approval, issuerId);
    if (userCardId == null) {
      // 보유카드와 매칭되지 않는 승인건은 적재하지 않고 미매칭으로 남긴다.
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

  /** 승인내역 적재 과정에서 드랍된 사유를 집계해 진단 로그로 남기기 위한 카운터다. */
  private static final class IngestStats {
    private int fetched;
    private int filtered;
    private int unmatched;
    private int invalid;
    private int duplicate;
  }

  /** 승인번호가 있으면 (카드+승인번호), 없으면 (카드+시각+금액+가맹점명)으로 중복 판정 키를 만든다. */
  private String dedupeKey(
      String userCardId,
      String approvalNumber,
      LocalDateTime approvedAt,
      int amount,
      String merchantName) {
    if (approvalNumber != null && !approvalNumber.isBlank()) {
      return userCardId + "A" + approvalNumber;
    }
    return userCardId + "B" + approvedAt + "" + amount + "" + merchantName;
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
