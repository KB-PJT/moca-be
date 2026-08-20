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
import com.moca.mocabe.domain.benefit.model.BenefitUsageCounts;
import com.moca.mocabe.domain.benefit.model.MonthlyBenefitLimit;
import com.moca.mocabe.domain.benefit.model.SimpleBenefitRuleRow;
import com.moca.mocabe.domain.codef.model.ApprovalInsert;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
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
    noop.calculateAndPersistForPeriod(
        "user-1", LocalDateTime.parse("2026-08-01T00:00:00"),
        LocalDateTime.parse("2026-09-01T00:00:00"));

    verify(mapper, never()).findApprovalsForCalculation(any());
    org.junit.jupiter.api.Assertions.assertFalse(noop.isEnabled());
    org.junit.jupiter.api.Assertions.assertTrue(service.isEnabled());
  }

  @Test
  void skipsPeriodBackfillWhenThereAreNoStoredApprovals() {
    when(mapper.findApprovalIdsForPeriod(eq("user-1"), any(LocalDateTime.class), any(LocalDateTime.class)))
        .thenReturn(List.of());

    service.calculateAndPersistForPeriod(
        "user-1", LocalDateTime.parse("2026-08-01T00:00:00"),
        LocalDateTime.parse("2026-09-01T00:00:00"));

    verify(mapper, never()).findApprovalsForCalculation(any());
  }

  @Test
  void periodBackfillHandlesNullIdsAndProcessesExistingApprovalIds() {
    when(mapper.findApprovalIdsForPeriod(eq("user-1"), any(LocalDateTime.class), any(LocalDateTime.class)))
        .thenReturn(null)
        .thenReturn(List.of("approval-1"));
    when(mapper.findApprovalsForCalculation(List.of("approval-1"))).thenReturn(List.of());

    service.calculateAndPersistForPeriod(
        "user-1", LocalDateTime.parse("2026-08-01T00:00:00"),
        LocalDateTime.parse("2026-09-01T00:00:00"));
    service.calculateAndPersistForPeriod(
        "user-1", LocalDateTime.parse("2026-08-01T00:00:00"),
        LocalDateTime.parse("2026-09-01T00:00:00"));

    verify(mapper).findApprovalsForCalculation(List.of("approval-1"));
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
    BenefitRule mileageRule =
        (BenefitRule)
            toRule.invoke(
                service,
                List.of(
                    new SimpleBenefitRuleRow(
                        "mile", "offer", "points", "mile", BigDecimal.ONE, null, null, null,
                        "all_merchants", "ALL", 1)),
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    assertEquals(com.moca.mocabe.domain.benefit.type.RewardUnit.POINT, pointRule.rewardUnit());
    assertEquals(com.moca.mocabe.domain.benefit.type.RewardUnit.MILE, mileageRule.rewardUnit());
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
  @DisplayName("자동 계산은 target snapshot 문자열이 아니라 category FK ID 계층을 사용한다")
  void matchesCategoryByForeignKeyIdInsteadOfSnapshotCode() {
    LocalDateTime approvedAt = LocalDateTime.of(2026, 8, 5, 3, 0);
    when(mapper.findApprovalsForCalculation(List.of("approval-1")))
        .thenReturn(List.of(new BenefitApprovalRow(
            "approval-1", "card-1", 15_000, approvedAt, "DENTAL", "merchant-id",
            "DENTAL,HOSPITAL", "dental-id,hospital-id")));
    when(mapper.findPreviousMonthSpend("card-1", "2026-07")).thenReturn(0);
    when(mapper.findSimpleRulesForUserCard(eq("card-1"), any()))
        .thenReturn(List.of(new SimpleBenefitRuleRow(
            "rule-1", "offer-1", "discount", "percent", new BigDecimal("10"),
            null, null, null, "merchant_category", "hospital-id", 1)));

    service.calculateAndPersist(List.of(new ApprovalInsert(
        "approval-1", "user-1", "card-1", null, "A-1", approvedAt, "치과", 15_000, "{ }")));

    verify(mapper).insertConfirmedUsage(
        any(), eq("card-1"), eq("offer-1"), eq("rule-1"), eq(null), eq("approval-1"),
        any(), eq(new BigDecimal("15000")), eq(new BigDecimal("1500")), eq(null), eq(null),
        eq(approvedAt));
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

  @Test
  void appliesJsonRuleWithLedgerFrequencyAndAvailablePerformance() {
    LocalDateTime approvedAt = LocalDateTime.of(2026, 8, 5, 3, 0);
    when(mapper.findApprovalsForCalculation(List.of("approval-json")))
        .thenReturn(
            List.of(
                new BenefitApprovalRow(
                    "approval-json",
                    "card-json",
                    20_000,
                    approvedAt,
                    "CAFE",
                    "merchant-id",
                    "CAFE,RESTAURANT",
                    "cafe-id,restaurant-id")));
    when(mapper.findPreviousMonthSpend("card-json", "2026-07")).thenReturn(500_000);
    when(mapper.findSimpleRulesForUserCard(eq("card-json"), any()))
        .thenReturn(List.of(jsonRule("json-rule", jsonDefinition())));
    when(mapper.findConfirmedUsageCounts(eq("card-json"), eq("json-offer"), any(), any(), any()))
        .thenReturn(new BenefitUsageCounts(0, 3));

    service.calculateAndPersist(
        List.of(
            new ApprovalInsert(
                "approval-json",
                "user-1",
                "card-json",
                null,
                "A-1",
                approvedAt,
                "카페",
                20_000,
                "{ }")));

    verify(mapper)
        .insertConfirmedUsage(
            any(),
            eq("card-json"),
            eq("json-offer"),
            eq("json-rule"),
            eq(null),
            eq("approval-json"),
            any(),
            eq(new BigDecimal("20000")),
            eq(new BigDecimal("2000")),
            eq(null),
            eq(null),
            eq(approvedAt));
  }

  @Test
  void failsJsonRuleClosedWhenPerformanceSnapshotOrJsonIsUnavailable() {
    LocalDateTime approvedAt = LocalDateTime.of(2026, 8, 5, 3, 0);
    when(mapper.findApprovalsForCalculation(List.of("approval-json")))
        .thenReturn(
            List.of(new BenefitApprovalRow("approval-json", "card-json", 20_000, approvedAt, null)));
    when(mapper.findPreviousMonthSpend("card-json", "2026-07")).thenReturn(null);
    when(mapper.findSimpleRulesForUserCard(eq("card-json"), any()))
        .thenReturn(
            List.of(
                jsonRule("json-rule-unavailable", jsonDefinition()),
                jsonRule("json-rule-invalid", "{")));

    service.calculateAndPersist(
        List.of(
            new ApprovalInsert(
                "approval-json",
                "user-1",
                "card-json",
                null,
                "A-1",
                approvedAt,
                "테스트",
                20_000,
                "{ }")));

    verify(mapper, never())
        .insertConfirmedUsage(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    verify(mapper, Mockito.times(2))
        .insertCalculationOutcome(
            any(),
            eq("card-json"),
            eq("approval-json"),
            eq("json-offer"),
            any(),
            eq(null),
            any(),
            eq("KRW"),
            eq(BigDecimal.ZERO),
            eq(BigDecimal.ZERO),
            eq(BigDecimal.ZERO),
            eq("not_applied"),
            eq("RULE_DATA_UNAVAILABLE"));
  }

  @Test
  void rejectsJsonRuleBeforeEvaluationWhenRelationalTargetDoesNotMatch() {
    LocalDateTime approvedAt = LocalDateTime.of(2026, 8, 5, 3, 0);
    when(mapper.findApprovalsForCalculation(List.of("approval-json")))
        .thenReturn(
            List.of(new BenefitApprovalRow("approval-json", "card-json", 20_000, approvedAt, null)));
    when(mapper.findPreviousMonthSpend("card-json", "2026-07")).thenReturn(null);
    SimpleBenefitRuleRow categoryRule =
        new SimpleBenefitRuleRow(
            "json-rule",
            "json-offer",
            "discount",
            "percent",
            new BigDecimal("10"),
            null,
            new BigDecimal("500000"),
            null,
            "merchant_category",
            "cafe-id",
            1,
            "include",
            null,
            jsonDefinition(),
            "SUPPORTED");
    when(mapper.findSimpleRulesForUserCard(eq("card-json"), any()))
        .thenReturn(List.of(categoryRule));

    service.calculateAndPersist(
        List.of(
            new ApprovalInsert(
                "approval-json",
                "user-1",
                "card-json",
                null,
                "A-1",
                approvedAt,
                "테스트",
                20_000,
                "{ }")));

    verify(mapper)
        .insertCalculationOutcome(
            any(),
            eq("card-json"),
            eq("approval-json"),
            eq("json-offer"),
            eq("json-rule"),
            eq(null),
            any(),
            eq("KRW"),
            eq(BigDecimal.ZERO),
            eq(BigDecimal.ZERO),
            eq(BigDecimal.ZERO),
            eq("not_applied"),
            eq("TARGET_NOT_MATCHED"));
  }

  private SimpleBenefitRuleRow jsonRule(String ruleId, String definition) {
    return new SimpleBenefitRuleRow(
        ruleId,
        "json-offer",
        "discount",
        "percent",
        new BigDecimal("10"),
        null,
        new BigDecimal("500000"),
        null,
        "all_merchants",
        "ALL",
        1,
        "include",
        null,
        definition,
        "SUPPORTED");
  }

  private String jsonDefinition() {
    return """
        {
          "schemaVersion":1,
          "conditions":{
            "all":[{
              "type":"PREVIOUS_MONTH_SPEND",
              "operator":"GTE",
              "value":"500000",
              "rejectionReason":"PERFORMANCE_NOT_MET"
            }]
          },
          "reward":{
            "benefitType":"DISCOUNT",
            "rewardUnit":"KRW",
            "calculation":"RATE",
            "rate":"0.10"
          },
          "limits":[{"type":"DAILY_USAGE_COUNT","value":"1"}]
        }
        """;
  }
}
