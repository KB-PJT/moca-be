package com.moca.mocabe.domain.benefit.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moca.mocabe.domain.benefit.mapper.BenefitCalculationMapper;
import com.moca.mocabe.domain.benefit.model.BenefitApprovalRow;
import com.moca.mocabe.domain.benefit.model.SimpleBenefitRuleRow;
import com.moca.mocabe.domain.codef.model.ApprovalInsert;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** seed에 존재하는 카드명을 사용해 승인부터 계산 산출물 저장까지의 대표 흐름을 검증한다. */
class BenefitReportSimulationTest {
  private static final String USER = "SIMULATION_USER_ID";
  private static final String WORK_CARD = "현대카드Z work Edition2";
  private static final String POINT_CARD = "올바른POINT체크카드";

  @Test
  @DisplayName("seed 카드의 8월 승인내역을 전월 실적 기준으로 계산해 적용·미적용 결과를 저장한다")
  void calculatesSeedCardApprovalsForAugustReport() {
    BenefitCalculationMapper mapper = Mockito.mock(BenefitCalculationMapper.class);
    BenefitUsageCalculationService service = new BenefitUsageCalculationService(mapper);
    LocalDateTime appliedAt = LocalDateTime.of(2026, 8, 5, 3, 0);
    LocalDateTime missedAt = LocalDateTime.of(2026, 8, 6, 3, 0);

    when(mapper.findApprovalsForCalculation(List.of("approval-work", "approval-point")))
        .thenReturn(
            List.of(
                new BenefitApprovalRow("approval-work", "work-card", 10_000, appliedAt, "CONV"),
                new BenefitApprovalRow("approval-point", "point-card", 20_000, missedAt, "CONV")));
    when(mapper.findPreviousMonthSpend("work-card", "2026-07")).thenReturn(350_000);
    when(mapper.findCurrentMonthSpend("work-card", "2026-08")).thenReturn(0);
    when(mapper.findPreviousMonthSpend("point-card", "2026-07")).thenReturn(250_000);
    when(mapper.findCurrentMonthSpend("point-card", "2026-08")).thenReturn(0);
    when(mapper.findSimpleRulesForUserCard(eq("work-card"), any()))
        .thenReturn(List.of(rule("work-rule", "work-offer", 300_000)));
    when(mapper.findSimpleRulesForUserCard(eq("point-card"), any()))
        .thenReturn(List.of(rule("point-rule", "point-offer", 400_000)));

    service.calculateAndPersist(
        List.of(
            approval("approval-work", "work-card", appliedAt, WORK_CARD, 10_000),
            approval("approval-point", "point-card", missedAt, POINT_CARD, 20_000)));

    verify(mapper).insertConfirmedUsage(
        any(), eq("work-card"), eq("work-offer"), eq("work-rule"), any(),
        eq("approval-work"), any(), eq(new BigDecimal("10000")), eq(new BigDecimal("1000")),
        any(), any(), eq(appliedAt));
    verify(mapper).insertCalculationOutcome(
        any(), eq("point-card"), eq("approval-point"), eq("point-offer"), eq("point-rule"),
        any(), any(), eq("KRW"), eq(new BigDecimal("2000")), eq(BigDecimal.ZERO),
        eq(new BigDecimal("2000")), eq("not_applied"), eq("PERFORMANCE_NOT_MET"));
  }

  private SimpleBenefitRuleRow rule(String ruleId, String offerId, int requiredSpend) {
    return new SimpleBenefitRuleRow(
        ruleId, offerId, "discount", "percent", new BigDecimal("10"), null,
        new BigDecimal(requiredSpend), null, "all_merchants", "ALL", 1);
  }

  private ApprovalInsert approval(
      String id, String cardId, LocalDateTime at, String cardName, int amount) {
    return new ApprovalInsert(id, USER, cardId, null, id, at, cardName + " 가맹점", amount, "{}");
  }
}
