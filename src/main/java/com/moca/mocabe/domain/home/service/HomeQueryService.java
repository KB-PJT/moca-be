package com.moca.mocabe.domain.home.service;

import com.moca.mocabe.domain.home.dto.HomeBenefitHighlightResponse;
import com.moca.mocabe.domain.home.dto.HomeCardResponse;
import com.moca.mocabe.domain.home.dto.HomeCardSummaryResponse;
import com.moca.mocabe.domain.home.dto.HomeCardsResponse;
import com.moca.mocabe.domain.home.dto.HomeGreetingResponse;
import com.moca.mocabe.domain.home.dto.RecentHistoryItemResponse;
import com.moca.mocabe.domain.home.dto.RecentHistoryResponse;
import com.moca.mocabe.domain.benefit.dto.PerformanceShortfallResponse;
import com.moca.mocabe.domain.benefit.mapper.BenefitHistoryMapper;
import com.moca.mocabe.domain.benefit.model.BenefitHistoryRow;
import com.moca.mocabe.domain.benefit.service.BenefitHistoryRepresentativeSelector;
import com.moca.mocabe.domain.home.mapper.HomeMapper;
import com.moca.mocabe.domain.home.model.HomeCardRow;
import com.moca.mocabe.domain.home.model.RecentHistoryRow;
import com.moca.mocabe.domain.user.mapper.UserMapper;
import com.moca.mocabe.domain.user.model.UserProfile;
import com.moca.mocabe.global.exception.home.InvalidHomeQueryException;
import com.moca.mocabe.global.exception.user.UserNotFoundException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.transaction.annotation.Transactional;

/** 홈 화면 컴포넌트별 조회 유스케이스를 담당한다. */
public class HomeQueryService {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final DateTimeFormatter YEAR_MONTH_FORMATTER =
      DateTimeFormatter.ofPattern("uuuu-MM", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

  private static final DateTimeFormatter OCCURRED_AT_FORMATTER =
      DateTimeFormatter.ISO_OFFSET_DATE_TIME;

  private final UserMapper userMapper;
  private final HomeMapper homeMapper;
  private final BenefitHistoryMapper benefitHistoryMapper;
  private final BenefitHistoryRepresentativeSelector representativeSelector;
  private final HomeCardsCache homeCardsCache;
  private final HomeGreetingCache homeGreetingCache;

  public HomeQueryService(UserMapper userMapper, HomeMapper homeMapper) {
    this(userMapper, homeMapper, null);
  }

  public HomeQueryService(
      UserMapper userMapper, HomeMapper homeMapper, BenefitHistoryMapper benefitHistoryMapper) {
    this(userMapper, homeMapper, benefitHistoryMapper, null, null);
  }

  public HomeQueryService(
      UserMapper userMapper, HomeMapper homeMapper, BenefitHistoryMapper benefitHistoryMapper,
      HomeCardsCache homeCardsCache, HomeGreetingCache homeGreetingCache) {
    this.userMapper = userMapper;
    this.homeMapper = homeMapper;
    this.benefitHistoryMapper = benefitHistoryMapper;
    this.representativeSelector = new BenefitHistoryRepresentativeSelector();
    this.homeCardsCache = homeCardsCache;
    this.homeGreetingCache = homeGreetingCache;
  }

  @Transactional(readOnly = true)
  public HomeGreetingResponse getGreeting(String userId, String requestedYearMonth) {
    String yearMonth = normalizeYearMonth(requestedYearMonth);
    UserProfile profile = requireProfile(userId);
    long missedBenefitAmount = resolveMissedBenefitAmount(userId, yearMonth);
    String message =
        missedBenefitAmount > 0
            ? String.format(Locale.KOREA, "이번 달 혜택 %,d원을 놓치고 있어요!", missedBenefitAmount)
            : "이번 달 놓친 혜택이 없습니다.";
    return new HomeGreetingResponse(profile.getNickname(), yearMonth, missedBenefitAmount, message);
  }

  @Transactional(readOnly = true)
  public HomeCardsResponse getCards(
      String userId, String requestedYearMonth, String requestedOrderMode) {
    String yearMonth = normalizeYearMonth(requestedYearMonth);
    UserProfile profile = requireProfile(userId);
    String orderMode = normalizeOrderMode(requestedOrderMode, profile.getCardSortMode());
    List<HomeCardRow> rows = resolveHomeCardRows(userId, yearMonth);
    List<HomeCardResponse> cards = mapCards(rows, orderMode);
    String selectedUserCardId = cards.isEmpty() ? null : cards.get(0).getUserCardId();
    return new HomeCardsResponse(yearMonth, orderMode, selectedUserCardId, cards);
  }

  @Transactional(readOnly = true)
  public RecentHistoryResponse getRecentHistory(
      String userId, String requestedYearMonth, int limit, String userCardId) {
    requireProfile(userId);
    YearMonth yearMonth = parseYearMonth(requestedYearMonth);
    if (limit < 1 || limit > 5) {
      throw new InvalidHomeQueryException("limit은 1에서 5 사이여야 합니다.");
    }
    LocalDateTime fromUtc =
        yearMonth
            .atDay(1)
            .atStartOfDay(SEOUL)
            .withZoneSameInstant(ZoneOffset.UTC)
            .toLocalDateTime();
    LocalDateTime toUtc =
        yearMonth
            .plusMonths(1)
            .atDay(1)
            .atStartOfDay(SEOUL)
            .withZoneSameInstant(ZoneOffset.UTC)
            .toLocalDateTime();
    List<RecentHistoryRow> rows = findRecentHistory(userId, fromUtc, toUtc, limit, userCardId);
    List<RecentHistoryItemResponse> history =
        (rows == null ? List.<RecentHistoryRow>of() : rows)
            .stream().map(this::toRecentHistory).toList();
    return new RecentHistoryResponse(history);
  }

  private List<RecentHistoryRow> findRecentHistory(
      String userId, LocalDateTime fromUtc, LocalDateTime toUtc, int limit, String userCardId) {
    if (benefitHistoryMapper == null) {
      return homeMapper.findRecentHistory(userId, fromUtc, toUtc, limit, userCardId);
    }
    List<BenefitHistoryRow> candidates =
        benefitHistoryMapper.findHistory(
            userId, fromUtc, toUtc, userCardId, null, "LATEST", 0, limit);
    return representativeSelector.select(candidates).stream().map(this::toRecentHistoryRow).toList();
  }

  private RecentHistoryRow toRecentHistoryRow(BenefitHistoryRow source) {
    RecentHistoryRow target = new RecentHistoryRow();
    target.setApprovalId(source.getApprovalId());
    target.setBenefitHistoryId(source.getBenefitHistoryId());
    target.setMerchantName(source.getMerchantName());
    target.setBenefitType(source.getBenefitType());
    target.setBenefitTitle(source.getBenefitTitle());
    target.setCardName(source.getCardName());
    target.setPaymentAmount(source.getPaymentAmount());
    target.setBenefitAmount(source.getBenefitAmount());
    target.setMissedBenefitAmount(source.getMissedBenefitAmount());
    target.setCalculationStatus(source.getCalculationStatus());
    target.setRejectionReason(source.getRejectionReason());
    target.setOccurredAt(source.getApprovedAt());
    target.setRequiredPreviousSpendAmount(source.getRequiredPreviousSpendAmount());
    target.setPreviousMonthSpendAmount(source.getPreviousMonthSpendAmount());
    target.setMonthlyUsedAmount(source.getMonthlyUsedAmount());
    target.setMonthlyLimitAmount(source.getMonthlyLimitAmount());
    return target;
  }

  private long resolveMissedBenefitAmount(String userId, String yearMonth) {
    if (homeGreetingCache != null) {
      Long cached = homeGreetingCache.get(userId, yearMonth);
      if (cached != null) {
        return cached;
      }
    }
    Long missed = homeMapper.sumMissedBenefitAmount(userId, yearMonth);
    long missedBenefitAmount = missed == null ? 0L : missed;
    if (homeGreetingCache != null) {
      homeGreetingCache.put(userId, yearMonth, missedBenefitAmount);
    }
    return missedBenefitAmount;
  }

  private List<HomeCardRow> resolveHomeCardRows(String userId, String yearMonth) {
    if (homeCardsCache != null) {
      List<HomeCardRow> cached = homeCardsCache.get(userId, yearMonth);
      if (cached != null) {
        return cached;
      }
    }
    List<HomeCardRow> rows = homeMapper.findHomeCards(userId, yearMonth);
    if (homeCardsCache != null) {
      homeCardsCache.put(userId, yearMonth, rows == null ? List.of() : rows);
    }
    return rows;
  }

  private UserProfile requireProfile(String userId) {
    UserProfile profile = userMapper.findProfileById(userId);
    if (profile == null) {
      throw new UserNotFoundException();
    }
    return profile;
  }

  private List<HomeCardResponse> mapCards(List<HomeCardRow> rows, String orderMode) {
    List<HomeCardRow> orderedRows =
        rows == null ? List.of() : rows.stream().sorted(cardComparator(orderMode)).toList();
    return java.util.stream.IntStream.range(0, orderedRows.size())
        .mapToObj(index -> toHomeCard(orderedRows.get(index), index + 1, orderMode))
        .toList();
  }

  private Comparator<HomeCardRow> cardComparator(String orderMode) {
    if (!"AUTO".equals(orderMode)) {
      return Comparator.comparingInt(HomeCardRow::getDisplayOrder)
          .thenComparing(HomeCardRow::getUserCardId);
    }
    return Comparator.comparing((HomeCardRow row) -> !hasPerformanceTarget(row))
        .thenComparingLong(this::performanceRemainingAmount)
        .thenComparing(Comparator.comparingLong(this::availableBenefitAmount).reversed())
        .thenComparingInt(HomeCardRow::getDisplayOrder)
        .thenComparing(HomeCardRow::getUserCardId);
  }

  private HomeCardResponse toHomeCard(HomeCardRow row, int order, String orderMode) {
    String reason = "AUTO".equals(orderMode) && order == 1 ? "다음 실적 구간까지 남은 금액이 가장 적은 카드" : null;
    long availableBenefitAmount = availableBenefitAmount(row);
    long remainingAmount = performanceRemainingAmount(row);
    int performanceRate =
        performanceRate(row.getPerformanceCurrentAmount(), row.getPerformanceTargetAmount());
    String monthlyLimitText =
        row.getMaximumMonthlyBenefitAmount() > 0
            ? String.format(Locale.KOREA, "월 최대 %,d원", row.getMaximumMonthlyBenefitAmount())
            : null;
    HomeBenefitHighlightResponse highlight =
        new HomeBenefitHighlightResponse(row.getHighlightBenefitTitle(), monthlyLimitText);
    HomeCardSummaryResponse summary =
        new HomeCardSummaryResponse(
            row.getReceivedBenefitAmount(),
            availableBenefitAmount,
            row.getMaximumMonthlyBenefitAmount(),
            row.getPerformanceCurrentAmount(),
            row.getPerformanceTargetAmount(),
            performanceRate,
            remainingAmount);
    return new HomeCardResponse(
        row.getUserCardId(),
        order,
        row.getCardName(),
        row.getAlias(),
        row.getCardImageUrl(),
        reason,
        highlight,
        summary);
  }

  private RecentHistoryItemResponse toRecentHistory(RecentHistoryRow row) {
    String occurredAt =
        row.getOccurredAt()
            .atZone(ZoneOffset.UTC)
            .withZoneSameInstant(SEOUL)
            .format(OCCURRED_AT_FORMATTER);
    return new RecentHistoryItemResponse(
        row.getApprovalId(),
        row.getBenefitHistoryId(),
        row.getMerchantName(),
        row.getBenefitType(),
        row.getBenefitTitle(),
        row.getCardName(),
        row.getPaymentAmount(),
        row.getBenefitAmount(),
        row.getMissedBenefitAmount(),
        row.getCalculationStatus(),
        row.getRejectionReason(),
        occurredAt,
        performanceShortfall(row),
        monthlyBenefitUsed(row),
        monthlyBenefitLimit(row));
  }

  private PerformanceShortfallResponse performanceShortfall(RecentHistoryRow row) {
    if (!"PERFORMANCE_NOT_MET".equals(row.getRejectionReason())
        || row.getRequiredPreviousSpendAmount() == null) {
      return null;
    }
    long required = row.getRequiredPreviousSpendAmount();
    long achieved = row.getPreviousMonthSpendAmount() == null ? 0 : row.getPreviousMonthSpendAmount();
    return new PerformanceShortfallResponse(required, achieved, Math.max(0, required - achieved));
  }

  private boolean hasMonthlyBenefitUsage(RecentHistoryRow row) {
    return isAppliedStatus(row.getCalculationStatus()) && row.getMonthlyLimitAmount() > 0;
  }

  private boolean isAppliedStatus(String status) {
    return "APPLIED".equals(status) || "PARTIALLY_APPLIED".equals(status);
  }

  private Long monthlyBenefitUsed(RecentHistoryRow row) {
    return hasMonthlyBenefitUsage(row) ? row.getMonthlyUsedAmount() : null;
  }

  private Long monthlyBenefitLimit(RecentHistoryRow row) {
    return hasMonthlyBenefitUsage(row) ? row.getMonthlyLimitAmount() : null;
  }

  private long performanceRemainingAmount(HomeCardRow row) {
    return Math.max(0, row.getPerformanceTargetAmount() - row.getPerformanceCurrentAmount());
  }

  private boolean hasPerformanceTarget(HomeCardRow row) {
    return row.getPerformanceTargetAmount() > 0;
  }

  private long availableBenefitAmount(HomeCardRow row) {
    return Math.max(0, row.getMaximumMonthlyBenefitAmount() - row.getReceivedBenefitAmount());
  }

  private int performanceRate(long currentAmount, long targetAmount) {
    if (targetAmount <= 0) {
      return 0;
    }
    return (int) Math.min(100, currentAmount * 100 / targetAmount);
  }

  private String normalizeYearMonth(String requestedYearMonth) {
    return parseYearMonth(requestedYearMonth).format(YEAR_MONTH_FORMATTER);
  }

  private YearMonth parseYearMonth(String requestedYearMonth) {
    if (requestedYearMonth == null || requestedYearMonth.isBlank()) {
      return YearMonth.now(SEOUL);
    }
    try {
      return YearMonth.parse(requestedYearMonth, YEAR_MONTH_FORMATTER);
    } catch (DateTimeParseException exception) {
      throw new InvalidHomeQueryException("yearMonth는 YYYY-MM 형식이어야 합니다.");
    }
  }

  private String normalizeOrderMode(String requestedOrderMode, String savedOrderMode) {
    String orderMode =
        requestedOrderMode == null || requestedOrderMode.isBlank()
            ? savedOrderMode
            : requestedOrderMode;
    if (orderMode == null || orderMode.isBlank()) {
      return "MANUAL";
    }
    String normalized = orderMode.toUpperCase(Locale.ROOT);
    if (!"AUTO".equals(normalized) && !"MANUAL".equals(normalized)) {
      throw new InvalidHomeQueryException("orderMode는 AUTO 또는 MANUAL이어야 합니다.");
    }
    return normalized;
  }
}
