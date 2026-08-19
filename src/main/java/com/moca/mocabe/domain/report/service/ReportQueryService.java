package com.moca.mocabe.domain.report.service;

import com.moca.mocabe.domain.codef.exception.UserCardNotFoundException;
import com.moca.mocabe.domain.report.dto.BenefitBreakdownResponse;
import com.moca.mocabe.domain.report.dto.BenefitCategoriesReportResponse;
import com.moca.mocabe.domain.report.dto.BenefitCategoryResponse;
import com.moca.mocabe.domain.report.dto.BenefitSummaryReportResponse;
import com.moca.mocabe.domain.report.dto.MissedBenefitItemResponse;
import com.moca.mocabe.domain.report.dto.MissedBenefitsReportResponse;
import com.moca.mocabe.domain.report.dto.PerformanceCardResponse;
import com.moca.mocabe.domain.report.dto.PerformanceCardsReportResponse;
import com.moca.mocabe.domain.report.dto.PerformanceSummaryCardResponse;
import com.moca.mocabe.domain.report.dto.PerformanceSummaryReportResponse;
import com.moca.mocabe.domain.report.dto.PerformanceTierResponse;
import com.moca.mocabe.domain.report.dto.ReportUserCardResponse;
import com.moca.mocabe.domain.report.mapper.ReportMapper;
import com.moca.mocabe.domain.report.model.BenefitTypeAmountRow;
import com.moca.mocabe.domain.report.model.CategoryBenefitRow;
import com.moca.mocabe.domain.report.model.MissedBenefitDataCounts;
import com.moca.mocabe.domain.report.model.PerformanceCardRow;
import com.moca.mocabe.domain.report.model.PerformanceTierRow;
import com.moca.mocabe.domain.user.mapper.UserMapper;
import com.moca.mocabe.global.exception.report.InvalidReportQueryException;
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
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Locale;
import org.springframework.transaction.annotation.Transactional;

/** 혜택 이력에서 집계한 혜택·실적 리포트 조회 유스케이스다. */
public class ReportQueryService {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final DateTimeFormatter YEAR_MONTH_FORMATTER =
      DateTimeFormatter.ofPattern("uuuu-MM", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

  private final UserMapper userMapper;
  private final ReportMapper reportMapper;

  public ReportQueryService(UserMapper userMapper, ReportMapper reportMapper) {
    this.userMapper = userMapper;
    this.reportMapper = reportMapper;
  }

  @Transactional(readOnly = true)
  public BenefitSummaryReportResponse getBenefitSummary(String userId, String requestedYearMonth) {
    requireUser(userId);
    YearMonth yearMonth = parseYearMonth(requestedYearMonth);
    List<BenefitTypeAmountRow> current = findBenefitAmounts(userId, yearMonth);
    long total = total(current);
    long previous = total(findBenefitAmounts(userId, yearMonth.minusMonths(1)));
    List<BenefitBreakdownResponse> breakdown =
        current.stream()
            .map(
                row ->
                    new BenefitBreakdownResponse(
                        row.benefitType(), labelOf(row.benefitType()), row.amount()))
            .sorted(
                Comparator.comparingLong(BenefitBreakdownResponse::amount)
                    .reversed()
                    .thenComparing(BenefitBreakdownResponse::type))
            .toList();
    return new BenefitSummaryReportResponse(
        format(yearMonth), total, previous, total - previous, breakdown);
  }

  @Transactional(readOnly = true)
  public BenefitCategoriesReportResponse getBenefitCategories(
      String userId, String requestedYearMonth, int limit) {
    requireUser(userId);
    if (limit < 1 || limit > 3) {
      throw new InvalidReportQueryException("limit은 1에서 3 사이여야 합니다.");
    }
    YearMonth yearMonth = parseYearMonth(requestedYearMonth);
    List<CategoryBenefitRow> rows =
        reportMapper.findBenefitAmountsByCategory(
            userId, startOfMonthUtc(yearMonth), startOfMonthUtc(yearMonth.plusMonths(1)), limit);
    List<BenefitCategoryResponse> categories =
        java.util.stream.IntStream.range(0, rows.size())
            .mapToObj(
                index -> {
                  CategoryBenefitRow row = rows.get(index);
                  return new BenefitCategoryResponse(
                      index + 1, row.categoryCode(), row.categoryName(), row.benefitAmount());
                })
            .toList();
    return new BenefitCategoriesReportResponse(format(yearMonth), categories);
  }

  /** 선택 옵션과 실적에 따라 제공된 월 한도 중 사용하지 않은 금액을 '놓친 혜택'으로 반환한다. */
  @Transactional(readOnly = true)
  public MissedBenefitsReportResponse getMissedBenefits(
      String userId, String requestedYearMonth, String userCardId) {
    requireUser(userId);
    if (userCardId == null || userCardId.isBlank()) {
      throw new InvalidReportQueryException("userCardId는 필수입니다.");
    }
    YearMonth yearMonth = parseYearMonth(requestedYearMonth);
    PerformanceCardRow card =
        reportMapper.findPerformanceCard(userId, userCardId, format(yearMonth));
    if (card == null) {
      // Mapper가 user_id를 조건으로 사용하므로 타인의 카드 ID도 같은 404로 처리한다.
      throw new UserCardNotFoundException();
    }
    List<MissedBenefitItemResponse> benefits =
        reportMapper.findMonthlyRemainingBenefits(userId, userCardId, format(yearMonth)).stream()
            .map(
                row ->
                    new MissedBenefitItemResponse(
                        row.benefitRuleId(),
                        row.title(),
                        row.benefitType(),
                        row.usedAmount(),
                        row.limitAmount(),
                        Math.max(0, row.limitAmount() - row.usedAmount()),
                        "KRW"))
            .filter(row -> row.remainingAmount() > 0)
            .toList();
    long remaining = benefits.stream().mapToLong(MissedBenefitItemResponse::remainingAmount).sum();
    MissedBenefitDataCounts counts = reportMapper.findMissedBenefitDataCounts(
        userId, userCardId, format(yearMonth));
    if (counts == null) {
      counts = MissedBenefitDataCounts.empty();
    }
    return new MissedBenefitsReportResponse(
        format(yearMonth),
        new ReportUserCardResponse(card.userCardId(), card.cardName(), null),
        remaining,
        counts.approvalCount(),
        counts.outcomeCount(),
        counts.usageCount(),
        benefits);
  }

  @Transactional(readOnly = true)
  public PerformanceSummaryReportResponse getPerformanceSummary(
      String userId, String requestedYearMonth) {
    requireUser(userId);
    YearMonth yearMonth = parseYearMonth(requestedYearMonth);
    List<PerformanceCardRow> rows = reportMapper.findPerformanceCards(userId, format(yearMonth));
    int achieved = (int) rows.stream().filter(this::isAchieved).count();
    List<PerformanceSummaryCardResponse> cards =
        rows.stream()
            .limit(3)
            .map(
                row ->
                    new PerformanceSummaryCardResponse(
                        row.userCardId(), row.cardName(), achievementRate(row), isAchieved(row)))
            .toList();
    return new PerformanceSummaryReportResponse(format(yearMonth), rows.size(), achieved, cards);
  }

  @Transactional(readOnly = true)
  public PerformanceCardsReportResponse getPerformanceCards(
      String userId, String requestedYearMonth) {
    requireUser(userId);
    YearMonth yearMonth = parseYearMonth(requestedYearMonth);
    Map<String, List<PerformanceTierResponse>> tiersByCard =
        reportMapper.findPerformanceTiers(userId).stream()
            .collect(Collectors.groupingBy(
                PerformanceTierRow::userCardId,
                Collectors.mapping(
                    tier -> new PerformanceTierResponse(tier.tier(), tier.targetAmount()),
                    Collectors.collectingAndThen(
                        Collectors.toList(),
                        values -> values.stream()
                            .sorted(java.util.Comparator.comparingInt(PerformanceTierResponse::tier))
                            .toList()))));
    List<PerformanceCardResponse> cards =
        reportMapper.findPerformanceCards(userId, format(yearMonth)).stream()
            .map(
                row -> {
                  List<PerformanceTierResponse> tiers =
                      tiersByCard.getOrDefault(row.userCardId(), List.of());
                  TierProgress progress = resolveTierProgress(row, tiers);
                  return new PerformanceCardResponse(
                        row.userCardId(),
                        row.cardName(),
                        row.cardImageUrl(),
                        row.currentPerformanceAmount(),
                        progress.currentTierTargetAmount(),
                        progress.achievementRate(),
                        progress.currentTier(),
                        progress.nextTier(),
                        progress.currentTierAchieved(),
                        progress.remainingAmountToNextTier(),
                        progress.nextTierTargetAmount(),
                        tiers);
                })
            .toList();
    return new PerformanceCardsReportResponse(format(yearMonth), cards);
  }

  private List<BenefitTypeAmountRow> findBenefitAmounts(String userId, YearMonth yearMonth) {
    return reportMapper.findBenefitAmountsByType(
        userId, startOfMonthUtc(yearMonth), startOfMonthUtc(yearMonth.plusMonths(1)));
  }

  private long total(List<BenefitTypeAmountRow> rows) {
    return rows.stream().mapToLong(BenefitTypeAmountRow::amount).sum();
  }

  private void requireUser(String userId) {
    if (userMapper.findProfileById(userId) == null) {
      throw new UserNotFoundException();
    }
  }

  private YearMonth parseYearMonth(String value) {
    if (value == null || value.isBlank()) {
      return YearMonth.now(SEOUL);
    }
    try {
      return YearMonth.parse(value, YEAR_MONTH_FORMATTER);
    } catch (DateTimeParseException exception) {
      throw new InvalidReportQueryException("yearMonth는 YYYY-MM 형식이어야 합니다.");
    }
  }

  private LocalDateTime startOfMonthUtc(YearMonth yearMonth) {
    return yearMonth
        .atDay(1)
        .atStartOfDay(SEOUL)
        .withZoneSameInstant(ZoneOffset.UTC)
        .toLocalDateTime();
  }

  private String format(YearMonth yearMonth) {
    return yearMonth.format(YEAR_MONTH_FORMATTER);
  }

  private String labelOf(String type) {
    return switch (type) {
      case "DISCOUNT" -> "할인";
      case "CASHBACK" -> "캐시백";
      case "POINT" -> "포인트";
      default -> type;
    };
  }

  private boolean isAchieved(PerformanceCardRow row) {
    return row.currentTierTargetAmount() > 0
        && row.currentPerformanceAmount() >= row.currentTierTargetAmount();
  }

  private int achievementRate(PerformanceCardRow row) {
    if (row.currentTierTargetAmount() <= 0) {
      return 0;
    }
    return (int)
        Math.min(100, row.currentPerformanceAmount() * 100 / row.currentTierTargetAmount());
  }

  private long remainingToTarget(PerformanceCardRow row) {
    return Math.max(0, row.currentTierTargetAmount() - row.currentPerformanceAmount());
  }

  private TierProgress resolveTierProgress(
      PerformanceCardRow row, List<PerformanceTierResponse> tiers) {
    if (tiers.isEmpty()) {
      return new TierProgress(
          row.currentTier(),
          row.currentTierTargetAmount(),
          row.nextTier(),
          null,
          isAchieved(row),
          remainingToTarget(row),
          achievementRate(row));
    }
    int achievedIndex = -1;
    for (int index = 0; index < tiers.size(); index++) {
      if (row.currentPerformanceAmount() >= tiers.get(index).targetAmount()) {
        achievedIndex = index;
      }
    }
    PerformanceTierResponse current = achievedIndex < 0 ? null : tiers.get(achievedIndex);
    int nextIndex = achievedIndex + 1;
    PerformanceTierResponse next = nextIndex < tiers.size() ? tiers.get(nextIndex) : null;
    long targetForProgress = next == null
        ? current == null ? 0 : current.targetAmount()
        : next.targetAmount();
    int rate = targetForProgress <= 0 ? 0
        : (int) Math.min(100,
            row.currentPerformanceAmount() * 100 / targetForProgress);
    return new TierProgress(
        current == null ? 0 : current.tier(),
        current == null ? 0 : current.targetAmount(),
        next == null ? null : next.tier(),
        next == null ? null : next.targetAmount(),
        current != null,
        next == null
            ? 0
            : Math.max(0, next.targetAmount() - row.currentPerformanceAmount()),
        rate);
  }

  private record TierProgress(
      int currentTier,
      long currentTierTargetAmount,
      Integer nextTier,
      Long nextTierTargetAmount,
      boolean currentTierAchieved,
      long remainingAmountToNextTier,
      int achievementRate) { }
}
