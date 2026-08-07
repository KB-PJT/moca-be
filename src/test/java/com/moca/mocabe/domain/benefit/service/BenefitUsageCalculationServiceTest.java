package com.moca.mocabe.domain.benefit.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.moca.mocabe.domain.benefit.mapper.BenefitCalculationMapper;
import com.moca.mocabe.domain.benefit.model.BenefitApprovalRow;
import com.moca.mocabe.domain.benefit.model.BenefitCalculationResult;
import com.moca.mocabe.domain.benefit.model.BenefitRule;
import com.moca.mocabe.domain.benefit.model.MonthlyBenefitLimit;
import com.moca.mocabe.domain.benefit.model.SimpleBenefitRuleRow;
import com.moca.mocabe.domain.codef.model.ApprovalInsert;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class BenefitUsageCalculationServiceTest {
  private final BenefitCalculationMapper mapper = Mockito.mock(BenefitCalculationMapper.class);
  private final BenefitUsageCalculationService service = new BenefitUsageCalculationService(mapper);

  @Test
  void ignoresEmptyApprovalListsAndReportsNoopServiceAsDisabled() {
    BenefitUsageCalculationService noop = BenefitUsageCalculationService.noop();

    service.calculateAndPersist(List.of());
    service.calculateAndPersist(null);

    verify(mapper, never()).findApprovalsForCalculation(any());
    org.junit.jupiter.api.Assertions.assertFalse(noop.isEnabled());
  }

  @Test
  void convertsPointMileageAndCashbackRulesAndKeepsZeroRewardOutcomesSilent() throws Exception {
    java.lang.reflect.Method benefitType =
        BenefitUsageCalculationService.class.getDeclaredMethod(
            "benefitType", String.class, com.moca.mocabe.domain.benefit.type.RewardUnit.class);
    benefitType.setAccessible(true);
    assertEquals(
        com.moca.mocabe.domain.benefit.type.BenefitType.MILEAGE,
        benefitType.invoke(service, "discount", com.moca.mocabe.domain.benefit.type.RewardUnit.MILE));
    assertEquals(
        com.moca.mocabe.domain.benefit.type.BenefitType.CASHBACK,
        benefitType.invoke(service, "cashback", com.moca.mocabe.domain.benefit.type.RewardUnit.KRW));
    assertEquals(
        com.moca.mocabe.domain.benefit.type.BenefitType.POINT,
        benefitType.invoke(service, "points", com.moca.mocabe.domain.benefit.type.RewardUnit.POINT));
    java.lang.reflect.Method toRule =
        BenefitUsageCalculationService.class.getDeclaredMethod(
            "toRule", List.class, BigDecimal.class, BigDecimal.class);
    toRule.setAccessible(true);
    BenefitRule pointRule =
        (BenefitRule)
            toRule.invoke(
                service,
                List.of(
                    new SimpleBenefitRuleRow(
                        "point", "offer", "points", "point", BigDecimal.ONE, null, null, null,
                        "all_merchants", "ALL", 1)),
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    BenefitRule perSpendRule =
        (BenefitRule)
            toRule.invoke(
                service,
                List.of(
                    new SimpleBenefitRuleRow(
                        "cash", "offer", "cashback", "KRW", BigDecimal.ONE, new BigDecimal("1000"),
                        null, null, "all_merchants", "ALL", 1)),
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    assertEquals(com.moca.mocabe.domain.benefit.type.RewardUnit.POINT, pointRule.rewardUnit());
    assertEquals(
        com.moca.mocabe.domain.benefit.type.BenefitBasis.PER_SPEND_UNIT,
        perSpendRule.benefitBasis());
    java.lang.reflect.Method persistOutcome = BenefitUsageCalculationService.class.getDeclaredMethod(
        "persistOutcome", BenefitApprovalRow.class, LocalDate.class, SimpleBenefitRuleRow.class,
        MonthlyBenefitLimit.class, BenefitCalculationResult.class);
    persistOutcome.setAccessible(true);
    persistOutcome.invoke(
        service,
        new BenefitApprovalRow("a", "c", 1, LocalDateTime.now(), null),
        LocalDate.now(),
        new SimpleBenefitRuleRow(
            "r", "o", "discount", "percent", BigDecimal.ZERO, null, null, null, "all_merchants", "ALL", 1),
        null,
        new BenefitCalculationResult(
            "r", com.moca.mocabe.domain.benefit.type.BenefitType.DISCOUNT,
            com.moca.mocabe.domain.benefit.type.RewardUnit.KRW, true, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, com.moca.mocabe.domain.benefit.type.BenefitRejectionReason.NONE));
    verify(mapper, never())
        .insertCalculationOutcome(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void appliesSeededAllMerchantsPercentageRuleToNewApprovalOnly() {
    LocalDateTime approvedAt = LocalDateTime.of(2026, 8, 5, 3, 0); // KST 12:00
    ApprovalInsert inserted =
        new ApprovalInsert(
            "approval-1", "user-1", "card-1", null, "A-1", approvedAt, "테스트", 15_000, "{ }");
    when(mapper.findApprovalsForCalculation(List.of("approval-1")))
        .thenReturn(
            List.of(new BenefitApprovalRow("approval-1", "card-1", 15_000, approvedAt, null)));
    when(mapper.findPreviousMonthSpend("card-1", "2026-07")).thenReturn(0);
    when(mapper.findSimpleRulesForUserCard(eq("card-1"), any()))
        .thenReturn(
            List.of(
                new SimpleBenefitRuleRow(
                    "rule-1",
                    "offer-1",
                    "discount",
                    "percent",
                    new BigDecimal("10"),
                    null,
                    null,
                    null,
                    "all_merchants",
                    "ALL",
                    1)));

    service.calculateAndPersist(List.of(inserted));

    verify(mapper)
        .insertConfirmedUsage(
            any(),
            eq("card-1"),
            eq("offer-1"),
            eq("rule-1"),
            eq(null),
            eq("approval-1"),
            any(),
            eq(new BigDecimal("15000")),
            eq(new BigDecimal("1500")),
            eq(null),
            eq(null),
            eq(approvedAt));
    verify(mapper)
        .insertCalculationOutcome(
            any(),
            eq("card-1"),
            eq("approval-1"),
            eq("offer-1"),
            eq("rule-1"),
            eq(null),
            any(),
            eq("KRW"),
            eq(new BigDecimal("1500")),
            eq(new BigDecimal("1500")),
            eq(BigDecimal.ZERO),
            eq("applied"),
            eq("NONE"));
  }

  @Test
  void doesNotApplyCategoryRuleWhenMerchantCategoryIsNotMapped() {
    LocalDateTime approvedAt = LocalDateTime.of(2026, 8, 5, 3, 0);
    when(mapper.findApprovalsForCalculation(List.of("approval-1")))
        .thenReturn(
            List.of(new BenefitApprovalRow("approval-1", "card-1", 15_000, approvedAt, null)));
    when(mapper.findPreviousMonthSpend("card-1", "2026-07")).thenReturn(0);
    when(mapper.findSimpleRulesForUserCard(eq("card-1"), any()))
        .thenReturn(
            List.of(
                new SimpleBenefitRuleRow(
                    "rule-1",
                    "offer-1",
                    "discount",
                    "percent",
                    new BigDecimal("10"),
                    null,
                    null,
                    null,
                    "merchant_category",
                    "CAFE",
                    1)));

    service.calculateAndPersist(
        List.of(
            new ApprovalInsert(
                "approval-1", "user-1", "card-1", null, "A-1", approvedAt, "테스트", 15_000, "{ }")));

    verify(mapper, never())
        .insertConfirmedUsage(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    verify(mapper)
        .insertCalculationOutcome(
            any(),
            eq("card-1"),
            eq("approval-1"),
            eq("offer-1"),
            eq("rule-1"),
            eq(null),
            any(),
            eq("KRW"),
            eq(BigDecimal.ZERO),
            eq(BigDecimal.ZERO),
            eq(BigDecimal.ZERO),
            eq("not_applied"),
            eq("TARGET_NOT_MATCHED"));
  }

  @Test
  void capsRewardWithPreviousMonthTierAndSharedMonthlyUsage() {
    LocalDateTime approvedAt = LocalDateTime.of(2026, 8, 5, 3, 0);
    when(mapper.findApprovalsForCalculation(List.of("approval-1")))
        .thenReturn(
            List.of(new BenefitApprovalRow("approval-1", "card-1", 15_000, approvedAt, null)));
    when(mapper.findPreviousMonthSpend("card-1", "2026-07")).thenReturn(300_000);
    when(mapper.findSimpleRulesForUserCard(eq("card-1"), any()))
        .thenReturn(
            List.of(
                new SimpleBenefitRuleRow(
                    "rule-1",
                    "offer-1",
                    "discount",
                    "percent",
                    new BigDecimal("10"),
                    null,
                    new BigDecimal("200000"),
                    null,
                    "all_merchants",
                    "ALL",
                    1)));
    when(mapper.findApplicableMonthlyRewardLimit(
            eq("offer-1"), any(), eq(new BigDecimal("300000")), eq("KRW")))
        .thenReturn(new MonthlyBenefitLimit("policy-1", "cafe-shared", new BigDecimal("5000")));
    when(mapper.findConfirmedMonthlyRewardsForUpdate(
            eq("card-1"), eq("policy-1"), eq("cafe-shared"), any(), any(), eq("KRW")))
        .thenReturn(List.of(new BigDecimal("4000")));

    service.calculateAndPersist(
        List.of(
            new ApprovalInsert(
                "approval-1", "user-1", "card-1", null, "A-1", approvedAt, "테스트", 15_000, "{ }")));

    verify(mapper).lockUserCardForBenefitCalculation("card-1");
    verify(mapper)
        .insertConfirmedUsage(
            any(),
            eq("card-1"),
            eq("offer-1"),
            eq("rule-1"),
            eq("policy-1"),
            eq("approval-1"),
            any(),
            eq(new BigDecimal("15000")),
            eq(new BigDecimal("1000")),
            eq(null),
            eq(null),
            eq(approvedAt));
    verify(mapper)
        .insertCalculationOutcome(
            any(),
            eq("card-1"),
            eq("approval-1"),
            eq("offer-1"),
            eq("rule-1"),
            eq("policy-1"),
            any(),
            eq("KRW"),
            eq(new BigDecimal("1500")),
            eq(new BigDecimal("1000")),
            eq(new BigDecimal("500")),
            eq("partially_applied"),
            eq("NONE"));
  }

  @Test
  void recordsFullyExhaustedMonthlyLimitAsActualMissedBenefit() {
    LocalDateTime approvedAt = LocalDateTime.of(2026, 8, 5, 3, 0);
    when(mapper.findApprovalsForCalculation(List.of("approval-1")))
        .thenReturn(
            List.of(new BenefitApprovalRow("approval-1", "card-1", 15_000, approvedAt, null)));
    when(mapper.findPreviousMonthSpend("card-1", "2026-07")).thenReturn(300_000);
    when(mapper.findSimpleRulesForUserCard(eq("card-1"), any()))
        .thenReturn(
            List.of(
                new SimpleBenefitRuleRow(
                    "rule-1",
                    "offer-1",
                    "discount",
                    "percent",
                    new BigDecimal("10"),
                    null,
                    null,
                    null,
                    "all_merchants",
                    "ALL",
                    1)));
    when(mapper.findApplicableMonthlyRewardLimit(
            eq("offer-1"), any(), eq(new BigDecimal("300000")), eq("KRW")))
        .thenReturn(new MonthlyBenefitLimit("policy-1", null, new BigDecimal("5000")));
    when(mapper.findConfirmedMonthlyRewardsForUpdate(
            eq("card-1"), eq("policy-1"), eq(null), any(), any(), eq("KRW")))
        .thenReturn(List.of(new BigDecimal("5000")));

    service.calculateAndPersist(
        List.of(
            new ApprovalInsert(
                "approval-1", "user-1", "card-1", null, "A-1", approvedAt, "테스트", 15_000, "{ }")));

    verify(mapper, never())
        .insertConfirmedUsage(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    verify(mapper)
        .insertCalculationOutcome(
            any(),
            eq("card-1"),
            eq("approval-1"),
            eq("offer-1"),
            eq("rule-1"),
            eq("policy-1"),
            any(),
            eq("KRW"),
            eq(new BigDecimal("1500")),
            eq(BigDecimal.ZERO),
            eq(new BigDecimal("1500")),
            eq("not_applied"),
            eq("MONTHLY_LIMIT_EXHAUSTED"));
  }
}
