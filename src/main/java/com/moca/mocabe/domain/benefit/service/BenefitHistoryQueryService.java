package com.moca.mocabe.domain.benefit.service;

import com.moca.mocabe.domain.benefit.dto.BenefitHistoryDetailResponse;
import com.moca.mocabe.domain.benefit.dto.BenefitHistoryItemResponse;
import com.moca.mocabe.domain.benefit.dto.BenefitHistoryMetaResponse;
import com.moca.mocabe.domain.benefit.dto.BenefitHistoryResponse;
import com.moca.mocabe.domain.benefit.dto.BenefitHistorySummaryResponse;
import com.moca.mocabe.domain.benefit.dto.MonthlyLimitResponse;
import com.moca.mocabe.domain.benefit.dto.PerformanceShortfallResponse;
import com.moca.mocabe.domain.benefit.mapper.BenefitHistoryMapper;
import com.moca.mocabe.domain.benefit.model.BenefitHistoryDetailRow;
import com.moca.mocabe.domain.benefit.model.BenefitHistoryRow;
import com.moca.mocabe.domain.benefit.model.BenefitHistorySummaryRow;
import com.moca.mocabe.global.exception.benefit.BenefitHistoryNotFoundException;
import com.moca.mocabe.global.exception.benefit.InvalidBenefitHistoryQueryException;
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

/** 카드 결제 승인 전체와 계산된 혜택 정보의 목록·상세 조회를 담당한다. */
public class BenefitHistoryQueryService {
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final DateTimeFormatter YEAR_MONTH =
      DateTimeFormatter.ofPattern("uuuu-MM", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
  private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
  private final BenefitHistoryMapper benefitHistoryMapper;
  private final BenefitHistoryRepresentativeSelector representativeSelector;

  public BenefitHistoryQueryService(BenefitHistoryMapper benefitHistoryMapper) {
    this.benefitHistoryMapper = benefitHistoryMapper;
    this.representativeSelector = new BenefitHistoryRepresentativeSelector();
  }

  @Transactional(readOnly = true)
  public BenefitHistoryResponse getHistory(
      String userId,
      String requestedYearMonth,
      String userCardId,
      String requestedType,
      String requestedSort,
      Integer requestedPage,
      Integer requestedSize) {
    YearMonth month = parseMonth(requestedYearMonth);
    String type = parseType(requestedType);
    String sort = parseSort(requestedSort);
    int page = requestedPage == null ? 1 : requestedPage;
    int size = requestedSize == null ? 20 : requestedSize;
    if (page < 1 || size < 1 || size > 100) {
      throw new InvalidBenefitHistoryQueryException("page는 1 이상, size는 1에서 100 사이여야 합니다.");
    }
    LocalDateTime from = toUtc(month);
    LocalDateTime to = toUtc(month.plusMonths(1));
    String cardId = blankToNull(userCardId);
    List<BenefitHistoryRow> representatives =
        representativeSelector.select(benefitHistoryMapper.findHistory(userId, from, to, cardId, type));
    List<BenefitHistoryRow> ordered = representatives.stream().sorted(historyOrder(sort)).toList();
    long total = ordered.size();
    List<BenefitHistoryItemResponse> data =
        ordered.stream()
            .skip((long) (page - 1) * size)
            .limit(size)
            .map(this::toItem)
            .toList();
    BenefitHistorySummaryRow summary =
        benefitHistoryMapper.summarizeHistory(userId, from, to, cardId);
    return new BenefitHistoryResponse(
        data,
        new BenefitHistorySummaryResponse(
            summary.totalBenefitAmount(),
            summary.discountAmount(),
            summary.cashbackAmount(),
            summary.pointAmount(),
            summary.mileageAmount()),
        new BenefitHistoryMetaResponse(page, size, total, (long) page * size < total));
  }

  private Comparator<BenefitHistoryRow> historyOrder(String sort) {
    Comparator<BenefitHistoryRow> latest =
        Comparator.comparing(BenefitHistoryRow::getApprovedAt)
            .reversed()
            .thenComparing(BenefitHistoryRow::getBenefitHistoryId, Comparator.reverseOrder());
    if (!"BENEFIT_DESC".equals(sort)) {
      return latest;
    }
    return Comparator.comparingLong(
            (BenefitHistoryRow row) ->
                Math.max(row.getBenefitAmount(), row.getMissedBenefitAmount()))
        .reversed()
        .thenComparing(latest);
  }

  @Transactional(readOnly = true)
  public BenefitHistoryDetailResponse getDetail(String userId, String benefitHistoryId) {
    if (benefitHistoryId == null || benefitHistoryId.isBlank()) {
      throw new InvalidBenefitHistoryQueryException("benefitHistoryId는 비어 있을 수 없습니다.");
    }
    BenefitHistoryDetailRow row = benefitHistoryMapper.findDetail(userId, benefitHistoryId);
    if (row == null) {
      throw new BenefitHistoryNotFoundException();
    }
    long used = row.getMonthlyUsedAmount(), limit = row.getMonthlyLimitAmount();
    return new BenefitHistoryDetailResponse(
        row.getBenefitHistoryId(),
        row.getCalculationStatus(),
        row.getMerchantName(),
        format(row.getApprovedAt()),
        row.getCardName(),
        row.getPaymentAmount(),
        row.getBenefitAmount(),
        row.getBenefitUnit(),
        row.getBenefitType(),
        row.getBenefitTitle(),
        new MonthlyLimitResponse(used, limit, Math.max(0, limit - used)),
        row.getEarnedMileage(),
        row.getMissedBenefitAmount(),
        row.getRejectionReason(),
        performanceShortfall(row));
  }

  private BenefitHistoryItemResponse toItem(BenefitHistoryRow row) {
    return new BenefitHistoryItemResponse(
        row.getBenefitHistoryId(),
        row.getMerchantName(),
        format(row.getApprovedAt()),
        row.getPaymentAmount(),
        row.getBenefitAmount(),
        row.getBenefitUnit(),
        row.getBenefitType(),
        row.getBenefitTitle(),
        row.getUserCardId(),
        row.getCardName(),
        row.getCalculationStatus(),
        row.getMissedBenefitAmount(),
        row.getRejectionReason(),
        performanceShortfall(row));
  }

  private PerformanceShortfallResponse performanceShortfall(BenefitHistoryRow row) {
    if (!"PERFORMANCE_NOT_MET".equals(row.getRejectionReason())
        || row.getRequiredPreviousSpendAmount() == null) {
      return null;
    }
    long required = row.getRequiredPreviousSpendAmount();
    long achieved = row.getPreviousMonthSpendAmount() == null ? 0 : row.getPreviousMonthSpendAmount();
    return new PerformanceShortfallResponse(required, achieved, Math.max(0, required - achieved));
  }

  private String format(LocalDateTime value) {
    return value.atZone(ZoneOffset.UTC).withZoneSameInstant(SEOUL).format(DATE_TIME);
  }

  private LocalDateTime toUtc(YearMonth month) {
    return month.atDay(1).atStartOfDay(SEOUL).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
  }

  private YearMonth parseMonth(String value) {
    if (value == null || value.isBlank()) {
      return YearMonth.now(SEOUL);
    }
    try {
      return YearMonth.parse(value, YEAR_MONTH);
    } catch (DateTimeParseException e) {
      throw new InvalidBenefitHistoryQueryException("yearMonth는 YYYY-MM 형식이어야 합니다.");
    }
  }

  private String parseType(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String type = value.toUpperCase(Locale.ROOT);
    if (!List.of("DISCOUNT", "CASHBACK", "POINT", "MILEAGE").contains(type)) {
      throw new InvalidBenefitHistoryQueryException(
          "type은 DISCOUNT, CASHBACK, POINT, MILEAGE 중 하나여야 합니다.");
    }
    return type;
  }

  private String parseSort(String value) {
    if (value == null || value.isBlank()) {
      return "LATEST";
    }
    String sort = value.toUpperCase(Locale.ROOT);
    if (!List.of("LATEST", "BENEFIT_DESC").contains(sort)) {
      throw new InvalidBenefitHistoryQueryException("sort는 LATEST 또는 BENEFIT_DESC여야 합니다.");
    }
    return sort;
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
