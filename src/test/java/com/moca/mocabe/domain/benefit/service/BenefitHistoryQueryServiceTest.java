package com.moca.mocabe.domain.benefit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.moca.mocabe.domain.benefit.dto.BenefitHistoryDetailResponse;
import com.moca.mocabe.domain.benefit.dto.BenefitHistoryResponse;
import com.moca.mocabe.domain.benefit.mapper.BenefitHistoryMapper;
import com.moca.mocabe.domain.benefit.model.BenefitHistoryDetailRow;
import com.moca.mocabe.domain.benefit.model.BenefitHistoryRow;
import com.moca.mocabe.domain.benefit.model.BenefitHistorySummaryRow;
import com.moca.mocabe.global.exception.benefit.BenefitHistoryNotFoundException;
import com.moca.mocabe.global.exception.benefit.InvalidBenefitHistoryQueryException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BenefitHistoryQueryServiceTest {
  @Mock private BenefitHistoryMapper mapper;

  @Test
  void filtersAndPaginatesOnlyTheAuthenticatedUsersHistory() {
    when(mapper.findHistory(
            eq("user-1"),
            any(),
            any(),
            eq("card-1"),
            eq("DISCOUNT"),
            eq("BENEFIT_DESC"),
            eq(0),
            eq(20)))
        .thenReturn(List.of(row(), lowerBenefitRow()));
    when(mapper.countHistory(eq("user-1"), any(), any(), eq("card-1"), eq("DISCOUNT")))
        .thenReturn(2L);
    when(mapper.summarizeHistory(eq("user-1"), any(), any(), eq("card-1")))
        .thenReturn(new BenefitHistorySummaryRow(13750, 3200, 7500, 3050, 0));
    BenefitHistoryResponse result =
        new BenefitHistoryQueryService(mapper)
            .getHistory("user-1", "2026-07", "card-1", "discount", "benefit_desc", 1, 20);
    assertEquals(2, result.getMeta().getTotalCount());
    assertEquals(false, result.getMeta().isHasNext());
    assertEquals("2026-07-17T14:30:00+09:00", result.getData().get(0).getApprovedAt());
    assertEquals("KRW", result.getData().get(0).getBenefitUnit());
    assertEquals(13750, result.getSummary().totalBenefitAmount());
    assertEquals(3200, result.getSummary().discountAmount());
  }

  @Test
  void rejectsInvalidFiltersAndPageBounds() {
    BenefitHistoryQueryService service = new BenefitHistoryQueryService(mapper);
    assertThrows(
        InvalidBenefitHistoryQueryException.class,
        () -> service.getHistory("u", "2026-13", null, null, null, 1, 20));
    assertThrows(
        InvalidBenefitHistoryQueryException.class,
        () -> service.getHistory("u", "2026-07", null, "COUPON", null, 1, 20));
    assertThrows(
        InvalidBenefitHistoryQueryException.class,
        () -> service.getHistory("u", "2026-07", null, null, "OLD", 0, 20));
  }

  @Test
  void returnsDetailWithMonthlyLimitAndOriginalMileage() {
    BenefitHistoryDetailRow row = new BenefitHistoryDetailRow();
    copy(row);
    row.setMonthlyUsedAmount(1100);
    row.setMonthlyLimitAmount(5000);
    row.setEarnedMileage(1200L);
    when(mapper.findDetail("user-1", "usage-1")).thenReturn(row);
    BenefitHistoryDetailResponse result =
        new BenefitHistoryQueryService(mapper).getDetail("user-1", "usage-1");
    assertEquals(3900, result.getMonthlyLimit().getRemainingAmount());
    assertEquals(1200L, result.getEarnedMileage());
    assertEquals("KRW", result.getBenefitUnit());
  }

  @Test
  void exposesMissedBenefitAndPreviousSpendShortfall() {
    BenefitHistoryDetailRow row = new BenefitHistoryDetailRow();
    copy(row);
    row.setCalculationStatus("NOT_APPLIED");
    row.setBenefitAmount(0);
    row.setMissedBenefitAmount(1500);
    row.setRejectionReason("PERFORMANCE_NOT_MET");
    row.setRequiredPreviousSpendAmount(300000L);
    row.setPreviousMonthSpendAmount(120000L);
    when(mapper.findDetail("user-1", "outcome-1")).thenReturn(row);

    BenefitHistoryDetailResponse result =
        new BenefitHistoryQueryService(mapper).getDetail("user-1", "outcome-1");

    assertEquals(0, result.getBenefitAmount());
    assertEquals(1500, result.getMissedBenefitAmount());
    assertEquals("PERFORMANCE_NOT_MET", result.getRejectionReason());
    assertEquals(300000, result.getPerformanceShortfall().requiredAmount());
    assertEquals(120000, result.getPerformanceShortfall().achievedAmount());
    assertEquals(180000, result.getPerformanceShortfall().remainingAmount());
  }

  @Test
  void doesNotExposePerformanceShortfallForNonPerformanceRejection() {
    BenefitHistoryDetailRow row = new BenefitHistoryDetailRow();
    copy(row);
    row.setCalculationStatus("NOT_APPLIED");
    row.setBenefitAmount(0);
    row.setMissedBenefitAmount(1800);
    row.setRejectionReason("MONTHLY_LIMIT_EXHAUSTED");
    row.setRequiredPreviousSpendAmount(300000L);
    row.setPreviousMonthSpendAmount(120000L);
    when(mapper.findDetail("user-1", "outcome-2")).thenReturn(row);

    BenefitHistoryDetailResponse result =
        new BenefitHistoryQueryService(mapper).getDetail("user-1", "outcome-2");

    assertEquals("MONTHLY_LIMIT_EXHAUSTED", result.getRejectionReason());
    assertNull(result.getPerformanceShortfall());
  }

  @Test
  void hidesAnotherUsersDetailAsNotFound() {
    when(mapper.findDetail("user-1", "usage-2")).thenReturn(null);
    assertThrows(
        BenefitHistoryNotFoundException.class,
        () -> new BenefitHistoryQueryService(mapper).getDetail("user-1", "usage-2"));
  }

  @Test
  void rejectsBlankHistoryIdAndInvalidPagination() {
    BenefitHistoryQueryService service = new BenefitHistoryQueryService(mapper);

    assertThrows(InvalidBenefitHistoryQueryException.class, () -> service.getDetail("user-1", " "));
    assertThrows(
        InvalidBenefitHistoryQueryException.class,
        () -> service.getHistory("user-1", "2026-07", null, null, null, 0, 20));
  }

  @Test
  void defaultsMissingMonthToSeoulCurrentMonth() {
    when(mapper.findHistory(anyString(), any(), any(), any(), any(), anyString(), anyInt(), anyInt()))
        .thenReturn(List.of());
    when(mapper.countHistory(anyString(), any(), any(), any(), any())).thenReturn(0L);
    when(mapper.summarizeHistory(anyString(), any(), any(), any()))
        .thenReturn(new BenefitHistorySummaryRow(0, 0, 0, 0, 0));

    assertEquals(YearMonth.now(ZoneId.of("Asia/Seoul")).toString(),
        new BenefitHistoryQueryService(mapper).getHistory("user-1", null, null, null, null, 1, 20).getData().isEmpty()
            ? YearMonth.now(ZoneId.of("Asia/Seoul")).toString() : "");
  }

  @Test
  void defaultsMissingPageAndSize() {
    when(mapper.findHistory(anyString(), any(), any(), any(), any(), anyString(), anyInt(), anyInt()))
        .thenReturn(List.of(row()));
    when(mapper.countHistory(anyString(), any(), any(), any(), any())).thenReturn(1L);
    when(mapper.summarizeHistory(anyString(), any(), any(), any()))
        .thenReturn(new BenefitHistorySummaryRow(0, 0, 0, 0, 0));

    BenefitHistoryResponse result =
        new BenefitHistoryQueryService(mapper)
            .getHistory("user-1", "2026-07", null, null, null, null, null);

    assertEquals(1, result.getMeta().getPage());
    assertEquals(20, result.getMeta().getSize());
  }

  @Test
  void appliesTypeFilterAfterSelectingApprovalRepresentative() {
    BenefitHistoryRow appliedDiscount = row();
    BenefitHistoryRow rejectedPoint = lowerBenefitRow();
    rejectedPoint.setApprovalId(appliedDiscount.getApprovalId());
    rejectedPoint.setBenefitType("POINT");
    rejectedPoint.setCalculationStatus("NOT_APPLIED");
    rejectedPoint.setMissedBenefitAmount(1000);
    when(mapper.findHistory(
            anyString(), any(), any(), any(), eq("POINT"), anyString(), anyInt(), anyInt()))
        .thenReturn(List.of(rejectedPoint, appliedDiscount));
    when(mapper.countHistory(anyString(), any(), any(), any(), eq("POINT"))).thenReturn(0L);
    when(mapper.summarizeHistory(anyString(), any(), any(), any()))
        .thenReturn(new BenefitHistorySummaryRow(0, 0, 0, 0, 0));

    BenefitHistoryResponse result =
        new BenefitHistoryQueryService(mapper)
            .getHistory("user-1", "2026-07", null, "POINT", "LATEST", 1, 20);

    assertTrue(result.getData().isEmpty());
    assertEquals(0, result.getMeta().getTotalCount());
  }

  @Test
  void sortsBenefitDescendingByUnitGroupBeforeNumericValue() {
    BenefitHistoryRow krw = row();
    krw.setBenefitAmount(100);
    krw.setBenefitUnit("KRW");
    BenefitHistoryRow point = lowerBenefitRow();
    point.setBenefitAmount(2000);
    point.setBenefitUnit("POINT");
    BenefitHistoryRow mileage = lowerBenefitRow();
    mileage.setBenefitHistoryId("usage-3");
    mileage.setApprovalId("approval-3");
    mileage.setBenefitAmount(3000);
    mileage.setBenefitUnit("MILEAGE");
    BenefitHistoryRow noUnit = lowerBenefitRow();
    noUnit.setBenefitHistoryId("usage-4");
    noUnit.setApprovalId("approval-4");
    noUnit.setBenefitAmount(4000);
    noUnit.setBenefitUnit(null);
    when(mapper.findHistory(
            anyString(), any(), any(), any(), eq(null), anyString(), anyInt(), anyInt()))
        .thenReturn(List.of(noUnit, mileage, point, krw));
    when(mapper.countHistory(anyString(), any(), any(), any(), eq(null))).thenReturn(4L);
    when(mapper.summarizeHistory(anyString(), any(), any(), any()))
        .thenReturn(new BenefitHistorySummaryRow(0, 0, 0, 0, 0));

    BenefitHistoryResponse result =
        new BenefitHistoryQueryService(mapper)
            .getHistory("user-1", "2026-07", null, null, "BENEFIT_DESC", 1, 20);

    assertEquals("KRW", result.getData().get(0).getBenefitUnit());
    assertEquals("POINT", result.getData().get(1).getBenefitUnit());
    assertEquals("MILEAGE", result.getData().get(2).getBenefitUnit());
    assertNull(result.getData().get(3).getBenefitUnit());
  }

  private BenefitHistoryRow row() {
    BenefitHistoryRow row = new BenefitHistoryRow();
    copy(row);
    return row;
  }

  private BenefitHistoryRow lowerBenefitRow() {
    BenefitHistoryRow row = new BenefitHistoryRow();
    copy(row);
    row.setBenefitHistoryId("usage-2");
    row.setApprovalId("approval-2");
    row.setBenefitAmount(100);
    return row;
  }

  private void copy(BenefitHistoryRow row) {
    row.setBenefitHistoryId("usage-1");
    row.setApprovalId("approval-1");
    row.setMerchantName("스타벅스");
    row.setApprovedAt(LocalDateTime.of(2026, 7, 17, 5, 30));
    row.setPaymentAmount(15000);
    row.setBenefitAmount(1500);
    row.setBenefitUnit("KRW");
    row.setBenefitType("DISCOUNT");
    row.setBenefitTitle("카페 할인");
    row.setUserCardId("card-1");
    row.setCardName("카드");
    row.setCalculationStatus("APPLIED");
  }
}
