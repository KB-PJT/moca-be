package com.moca.mocabe.domain.benefit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moca.mocabe.domain.benefit.mapper.BenefitCalculationMapper;
import com.moca.mocabe.domain.benefit.model.BenefitAreaSpendRow;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@DisplayName("월간 혜택 영역 사용액 서비스")
class BenefitAreaSpendServiceTest {
  private final BenefitCalculationMapper mapper = Mockito.mock(BenefitCalculationMapper.class);
  private final BenefitAreaSpendService service = new BenefitAreaSpendService(mapper);

  @Test
  @DisplayName("월 집계 조회와 최다 영역 선정을 Mapper 결과에 연결한다")
  void findsMonthlySpendsAndTopArea() {
    BenefitAreaSpendRow row = new BenefitAreaSpendRow(
        "DREAM", "RETAIL_STORE", "편의점·잡화", 2, new BigDecimal("10000"), 1);
    when(mapper.findMonthlyBenefitAreaSpends("card-1", "DREAM", "2026-08"))
        .thenReturn(List.of(row));

    assertEquals(List.of(row), service.findMonthlySpends(
        "card-1", "DREAM", YearMonth.of(2026, 8)));
    assertEquals(row, service.findTopArea("card-1", "DREAM", YearMonth.of(2026, 8)));
  }

  @Test
  @DisplayName("필수 조회값이 없으면 빈 결과를 반환한다")
  void returnsEmptyForMissingLookupValues() {
    assertEquals(List.of(), service.findMonthlySpends(null, "DREAM", YearMonth.of(2026, 8)));
    assertEquals(List.of(), service.findMonthlySpends("card-1", null, YearMonth.of(2026, 8)));
    assertEquals(List.of(), service.findMonthlySpends("card-1", "DREAM", null));
    assertEquals(List.of(), service.findAreaKeysForApproval(null, "DREAM"));
    assertEquals(List.of(), service.findAreaKeysForApproval("approval-1", null));
    assertNull(new BenefitAreaSpendService(null)
        .findTopArea("card-1", "DREAM", YearMonth.of(2026, 8)));
  }

  @Test
  @DisplayName("동일 승인 이벤트가 처음 적재될 때만 월 집계를 증가시킨다")
  void recordsOnlyNewApprovalEvent() {
    when(mapper.findBenefitAreaKeysForApproval("approval-1", "DREAM"))
        .thenReturn(List.of("RETAIL_STORE", "ENJOY_STORE"));
    when(mapper.insertBenefitAreaSpendEventIfAbsent(
        "approval-1", "card-1", "DREAM", "RETAIL_STORE", "2026-08",
        new BigDecimal("10000"))).thenReturn(1);

    service.recordApproval("approval-1", "card-1", new BigDecimal("10000"),
        YearMonth.of(2026, 8));

    verify(mapper).upsertMonthlyBenefitAreaSpend(
        "card-1", "DREAM", "RETAIL_STORE", "2026-08", new BigDecimal("10000"));
    verify(mapper, never()).upsertMonthlyBenefitAreaSpend(
        "card-1", "DREAM", "ENJOY_STORE", "2026-08", new BigDecimal("10000"));
  }

  @Test
  @DisplayName("영역 적재 필수값이 없으면 원장을 변경하지 않는다")
  void ignoresMissingRecordValues() {
    service.recordApproval(null, "card-1", BigDecimal.ONE, YearMonth.of(2026, 8));
    service.recordApproval("approval-1", null, BigDecimal.ONE, YearMonth.of(2026, 8));
    service.recordApproval("approval-1", "card-1", null, YearMonth.of(2026, 8));
    service.recordApproval("approval-1", "card-1", BigDecimal.ONE, null);
    new BenefitAreaSpendService(null).recordApproval(
        "approval-1", "card-1", BigDecimal.ONE, YearMonth.of(2026, 8));

    verify(mapper, never()).findBenefitAreaKeysForApproval("approval-1", "DREAM");
  }
}
