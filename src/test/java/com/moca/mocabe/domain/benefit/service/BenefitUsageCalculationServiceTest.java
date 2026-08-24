package com.moca.mocabe.domain.benefit.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.moca.mocabe.domain.benefit.mapper.BenefitCalculationMapper;
import com.moca.mocabe.domain.benefit.model.BenefitApprovalRow;
import com.moca.mocabe.domain.benefit.model.BenefitCalculationResult;
import com.moca.mocabe.domain.benefit.model.BenefitRule;
import com.moca.mocabe.domain.benefit.model.BenefitUsageCounts;
import com.moca.mocabe.domain.benefit.model.BenefitLimitTierCandidate;
import com.moca.mocabe.domain.benefit.model.MonthlyBenefitLimit;
import com.moca.mocabe.domain.benefit.model.SimpleBenefitRuleRow;
import com.moca.mocabe.domain.codef.model.ApprovalInsert;
import com.moca.mocabe.global.exception.benefit.InvalidBenefitRecalculationException;
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
  void recalculatesRequestedMonthUsingAuthenticatedUserAndSeoulMonthBounds() {
    when(mapper.findApprovalIdsForPeriod(eq("user-1"), any(LocalDateTime.class), any(LocalDateTime.class)))
        .thenReturn(List.of());

    assertEquals("2026-08", service.recalculateForMonth("user-1", "2026-08"));

    verify(mapper).findApprovalIdsForPeriod(
        eq("user-1"),
        eq(LocalDateTime.of(2026, 7, 31, 15, 0)),
        eq(LocalDateTime.of(2026, 8, 31, 15, 0)));
  }

  @Test
  void defaultsRecalculationToCurrentMonthWhenYearMonthIsMissing() {
    when(mapper.findApprovalIdsForPeriod(eq("user-1"), any(LocalDateTime.class), any(LocalDateTime.class)))
        .thenReturn(List.of());

    service.recalculateForMonth("user-1", null);

    verify(mapper).findApprovalIdsForPeriod(eq("user-1"), any(LocalDateTime.class), any(LocalDateTime.class));
  }

  @Test
  void rejectsInvalidRecalculationMonth() {
    assertThrows(
        InvalidBenefitRecalculationException.class,
        () -> service.recalculateForMonth("user-1", "2026-13"));
    verify(mapper, never()).findApprovalIdsForPeriod(any(), any(), any());
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
  @DisplayName("기간 일부만 동기화해도 영향을 받는 보유 카드의 월 전체 승인을 재계산한다")
  void expandsPeriodRecalculationToWholeCardMonth() {
    LocalDateTime approvedAt = LocalDateTime.of(2026, 8, 12, 3, 0);
    when(mapper.findApprovalIdsForPeriod(eq("user-1"), any(), any()))
        .thenReturn(List.of("approval-new"));
    when(mapper.findApprovalsForCalculation(List.of("approval-new")))
        .thenReturn(List.of(new BenefitApprovalRow(
            "approval-new", "card-1", 10_000, approvedAt, null)));
    when(mapper.findApprovedApprovalIdsForCardMonth("card-1", "2026-08"))
        .thenReturn(List.of("approval-old", "approval-new"));
    when(mapper.hasBenefitOfferForUserCard(
        "card-1", "Deep Dream 모두드림 0.2%"))
        .thenReturn(true);
    when(mapper.findApprovalsForCalculation(List.of("approval-new", "approval-old")))
        .thenReturn(List.of());

    service.calculateAndPersistForPeriod(
        "user-1", LocalDateTime.of(2026, 8, 12, 0, 0),
        LocalDateTime.of(2026, 8, 13, 0, 0));

    verify(mapper).deleteCalculationOutcomes(List.of("approval-new", "approval-old"));
    verify(mapper).deleteBenefitUsages(List.of("approval-new", "approval-old"));
    verify(mapper).findApprovalsForCalculation(List.of("approval-new", "approval-old"));
  }

  @Test
  @DisplayName("Deep Dream 최다 영역은 기본 적립과 한도 내 추가 적립을 합산한다")
  void appliesDeepDreamTopAreaAndRemainingExtraLimit() {
    LocalDateTime approvedAt = LocalDateTime.of(2026, 8, 5, 3, 0);
    BenefitApprovalRow approval = new BenefitApprovalRow(
        "approval-dream", "card-dream", 100_000, approvedAt, null, "merchant-cu", null, null);
    SimpleBenefitRuleRow rule = new SimpleBenefitRuleRow(
        "rule-dream", "offer-dream", "Deep Dream 모두드림 0.2%", "points", "point",
        new BigDecimal("0.2"), null, null, null, "all_merchants", "ALL", 1,
        "include", null, null, "SUPPORTED");
    when(mapper.findApprovalsForCalculation(List.of("approval-dream")))
        .thenReturn(List.of(approval));
    when(mapper.hasBenefitOfferForUserCard(
        "card-dream", "Deep Dream 모두드림 0.2%"))
        .thenReturn(true);
    when(mapper.findApprovedApprovalIdsForCardMonth("card-dream", "2026-08"))
        .thenReturn(List.of("approval-dream"));
    when(mapper.findBenefitAreaKeysForApproval("approval-dream", "DREAM"))
        .thenReturn(List.of("RETAIL_STORE"));
    when(mapper.findMonthlyBenefitAreaSpends("card-dream", "DREAM", "2026-08"))
        .thenReturn(List.of(new com.moca.mocabe.domain.benefit.model.BenefitAreaSpendRow(
            "DREAM", "RETAIL_STORE", "편의점·잡화", 2, new BigDecimal("100000"), 1)));
    when(mapper.findPreviousMonthSpend("card-dream", "2026-07")).thenReturn(200_000);
    when(mapper.findSimpleRulesForUserCard(eq("card-dream"), any())).thenReturn(List.of(rule));
    when(mapper.findConfirmedDeepDreamExtraRewardForUpdate(eq("card-dream"), any(), any()))
        .thenReturn(new BigDecimal("4500"));

    service.calculateAndPersist(List.of(new ApprovalInsert(
        "approval-dream", "user-1", "card-dream", "merchant-cu", "A-DREAM", approvedAt,
        "CU", 100_000, "{}")));

    verify(mapper).insertConfirmedUsage(
        any(), eq("card-dream"), eq("offer-dream"), eq("rule-dream"), eq(null),
        eq("approval-dream"), any(), eq(new BigDecimal("100000")), eq(BigDecimal.ZERO),
        eq(new BigDecimal("700")), eq("point"), eq(approvedAt));
  }

  @Test
  @DisplayName("Deep Dream 비대상 가맹점은 모두드림 기본 0.2%만 적립한다")
  void keepsDeepDreamBaseRewardOutsideDreamArea() {
    LocalDateTime approvedAt = LocalDateTime.of(2026, 8, 5, 3, 0);
    when(mapper.findApprovalsForCalculation(List.of("approval-base")))
        .thenReturn(List.of(new BenefitApprovalRow(
            "approval-base", "card-dream", 100_000, approvedAt, null)));
    when(mapper.findPreviousMonthSpend("card-dream", "2026-07")).thenReturn(200_000);
    when(mapper.findSimpleRulesForUserCard(eq("card-dream"), any()))
        .thenReturn(List.of(new SimpleBenefitRuleRow(
            "rule-dream", "offer-dream", "Deep Dream 모두드림 0.2%", "points", "percent",
            new BigDecimal("0.2"), null, null, null, "all_merchants", "ALL", 1,
            "include", null, deepDreamDefinition(), "SUPPORTED")));
    when(mapper.findBenefitAreaKeysForApproval("approval-base", "DREAM"))
        .thenReturn(List.of());

    service.calculateAndPersist(List.of(new ApprovalInsert(
        "approval-base", "user-1", "card-dream", null, "A-BASE", approvedAt,
        "일반 가맹점", 100_000, "{}")));

    verify(mapper).insertConfirmedUsage(
        any(), eq("card-dream"), eq("offer-dream"), eq("rule-dream"), eq(null),
        eq("approval-base"), any(), eq(new BigDecimal("100000")), eq(BigDecimal.ZERO),
        eq(new BigDecimal("200")), eq("point"), eq(approvedAt));
  }

  @Test
  @DisplayName("SimpleBenefitRuleRow 기존 matchMode 생성자는 offerName 없이 호환된다")
  void keepsLegacySimpleRuleConstructor() {
    SimpleBenefitRuleRow row = new SimpleBenefitRuleRow(
        "rule", "offer", "points", "point", BigDecimal.ONE, null, null, null,
        "all_merchants", "ALL", 1, "include");

    assertEquals("include", row.matchMode());
    org.junit.jupiter.api.Assertions.assertNull(row.offerName());
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
    when(mapper.findCurrentMonthSpend("card-1", "2026-08")).thenReturn(450_000);
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
    when(mapper.findMonthlyRewardLimitCandidates(eq("offer-1"), any(), eq("KRW")))
        .thenReturn(List.of(new BenefitLimitTierCandidate("policy-1", "cafe-shared",
            new BigDecimal("5000"), new BigDecimal("300000"), BigDecimal.ZERO)));
    when(mapper.findConfirmedMonthlyRewardsForUpdate(
            eq("card-1"), eq("policy-1"), eq("cafe-shared"), any(), any(), eq("KRW")))
        .thenReturn(List.of(new BigDecimal("4000")));

    service.calculateAndPersist(
        List.of(
            new ApprovalInsert(
                "approval-1", "user-1", "card-1", null, "A-1", approvedAt, "테스트", 15_000, "{ }")));

    verify(mapper).findMonthlyRewardLimitCandidates(eq("offer-1"), any(), eq("KRW"));

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
    when(mapper.findMonthlyRewardLimitCandidates(eq("offer-1"), any(), eq("KRW")))
        .thenReturn(List.of(new BenefitLimitTierCandidate("policy-1", null,
            new BigDecimal("5000"), new BigDecimal("300000"), BigDecimal.ZERO)));
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
  @DisplayName("JSON 조건에 실적이 없어도 월 한도 tier 실적 미달을 놓친 혜택으로 기록한다")
  void recordsTierPerformanceFailureBeforeMonthlyLimitExhaustion() {
    LocalDateTime approvedAt = LocalDateTime.of(2026, 8, 5, 3, 0);
    when(mapper.findApprovalsForCalculation(List.of("approval-tier")))
        .thenReturn(List.of(
            new BenefitApprovalRow("approval-tier", "card-tier", 20_000, approvedAt, null)));
    when(mapper.findPreviousMonthSpend("card-tier", "2026-07")).thenReturn(250_000);
    when(mapper.findSimpleRulesForUserCard(eq("card-tier"), any()))
        .thenReturn(List.of(new SimpleBenefitRuleRow(
            "rule-tier", "offer-tier", "points", "point", new BigDecimal("5"),
            null, null, null, "all_merchants", "ALL", 1, "include", null,
            jsonDefinitionWithoutPerformance(), "SUPPORTED")));
    when(mapper.findMonthlyRewardLimitCandidates(eq("offer-tier"), any(), eq("point")))
        .thenReturn(List.of(new BenefitLimitTierCandidate(
            "policy-tier", null, new BigDecimal("3000"),
            new BigDecimal("300000"), BigDecimal.ZERO)));

    service.calculateAndPersist(List.of(new ApprovalInsert(
        "approval-tier", "user-1", "card-tier", null, "A-TIER", approvedAt,
        "CU", 20_000, "{}")));

    verify(mapper, never()).insertConfirmedUsage(
        any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    verify(mapper).insertCalculationOutcome(
        any(), eq("card-tier"), eq("approval-tier"), eq("offer-tier"), eq("rule-tier"),
        eq(null), any(), eq("POINT"), eq(new BigDecimal("1000")), eq(BigDecimal.ZERO),
        eq(new BigDecimal("1000")), eq("not_applied"), eq("PERFORMANCE_NOT_MET"));
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

  @Test
  @DisplayName("나라사랑 편의점 캐시백은 혜택별 월 잔여 한도까지만 적용한다")
  void capsNarasarangConvenienceCashbackByOfferLimit() {
    LocalDateTime approvedAt = LocalDateTime.of(2026, 8, 5, 3, 0);
    when(mapper.findApprovalsForCalculation(List.of("narasarang-approval")))
        .thenReturn(List.of(new BenefitApprovalRow(
            "narasarang-approval", "narasarang-card", 10_000, approvedAt, null)));
    when(mapper.findPreviousMonthSpend("narasarang-card", "2026-07")).thenReturn(200_000);
    when(mapper.findSimpleRulesForUserCard(eq("narasarang-card"), any()))
        .thenReturn(List.of(narasarangConvenienceRule()));
    when(mapper.findMonthlyOfferRewardLimit(
        eq("narasarang-offer"), any(), eq(new BigDecimal("200000")), eq("KRW")))
        .thenReturn(new BigDecimal("5000"));
    when(mapper.findConfirmedMonthlyRewardForOfferForUpdate(
        eq("narasarang-card"), eq("narasarang-offer"), any(), any()))
        .thenReturn(new BigDecimal("4500"));

    service.calculateAndPersist(List.of(new ApprovalInsert(
        "narasarang-approval", "user-1", "narasarang-card", null, "A-1", approvedAt,
        "CU", 10_000, "{}")));

    verify(mapper).insertConfirmedUsage(
        any(), eq("narasarang-card"), eq("narasarang-offer"),
        eq("narasarang-rule"), eq(null), eq("narasarang-approval"), any(),
        eq(new BigDecimal("10000")), eq(new BigDecimal("500")), eq(null), eq(null),
        eq(approvedAt));
  }

  @Test
  @DisplayName("나라사랑 편의점 월 캐시백 한도가 소진되면 미적용 결과를 저장한다")
  void rejectsNarasarangConvenienceCashbackWhenOfferLimitIsExhausted() {
    LocalDateTime approvedAt = LocalDateTime.of(2026, 8, 5, 3, 0);
    when(mapper.findApprovalsForCalculation(List.of("narasarang-exhausted")))
        .thenReturn(List.of(new BenefitApprovalRow(
            "narasarang-exhausted", "narasarang-card", 10_000, approvedAt, null)));
    when(mapper.findPreviousMonthSpend("narasarang-card", "2026-07")).thenReturn(200_000);
    when(mapper.findSimpleRulesForUserCard(eq("narasarang-card"), any()))
        .thenReturn(List.of(narasarangConvenienceRule()));
    when(mapper.findMonthlyOfferRewardLimit(
        eq("narasarang-offer"), any(), eq(new BigDecimal("200000")), eq("KRW")))
        .thenReturn(new BigDecimal("5000"));
    when(mapper.findConfirmedMonthlyRewardForOfferForUpdate(
        eq("narasarang-card"), eq("narasarang-offer"), any(), any()))
        .thenReturn(new BigDecimal("5000"));

    service.calculateAndPersist(List.of(new ApprovalInsert(
        "narasarang-exhausted", "user-1", "narasarang-card", null, "A-2", approvedAt,
        "CU", 10_000, "{}")));

    verify(mapper, never()).insertConfirmedUsage(
        any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    verify(mapper).insertCalculationOutcome(
        any(), eq("narasarang-card"), eq("narasarang-exhausted"),
        eq("narasarang-offer"), eq("narasarang-rule"), eq(null), any(), eq("KRW"),
        eq(new BigDecimal("2000")), eq(BigDecimal.ZERO), eq(new BigDecimal("2000")),
        eq("not_applied"), eq("MONTHLY_LIMIT_EXHAUSTED"));
  }

  private SimpleBenefitRuleRow narasarangConvenienceRule() {
    return new SimpleBenefitRuleRow(
        "narasarang-rule", "narasarang-offer", "편의점 20% 캐시백",
        "cashback", "percent", new BigDecimal("20"), null, new BigDecimal("100000"),
        null, "all_merchants", "ALL", 1, "include", null,
        """
            {"schemaVersion":1,"conditions":{"all":[],"any":[],"none":[]},
             "reward":{"benefitType":"CASHBACK","rewardUnit":"KRW",
                       "calculation":"RATE","rate":"0.20"},"limits":[]}
            """, "SUPPORTED");
  }

  @Test
  void appliesSolFuelRateToRemainingMonthlyEligibleSpend() {
    LocalDateTime approvedAt = LocalDateTime.of(2026, 8, 5, 3, 0);
    prepareSolApproval(approvedAt, 50_000, 1_000_000, "SK에너지", solSpecialRule());
    when(mapper.findConfirmedMonthlyEligibleSpendForUpdate(
        eq("sol-card"), eq("sol-special"), any(), any()))
        .thenReturn(new BigDecimal("280000"));

    service.calculateAndPersist(List.of(new ApprovalInsert(
        "sol-approval", "user-1", "sol-card", null, "A-1", approvedAt,
        "SK에너지", 50_000, "{ }")));

    verify(mapper).insertConfirmedUsage(
        any(), eq("sol-card"), eq("sol-special"), eq("sol-rule"), eq(null),
        eq("sol-approval"), any(), eq(new BigDecimal("20000")),
        eq(BigDecimal.ZERO), eq(new BigDecimal("1000")), eq("point"), eq(approvedAt));
  }

  @Test
  void excludesUnsupportedSolOnlineMerchantFromAutomaticCalculation() {
    LocalDateTime approvedAt = LocalDateTime.of(2026, 8, 5, 3, 0);
    prepareSolApproval(approvedAt, 100_000, 1_000_000, "쿠팡", solBasicRule());

    service.calculateAndPersist(List.of(new ApprovalInsert(
        "sol-approval", "user-1", "sol-card", null, "A-1", approvedAt,
        "쿠팡", 100_000, "{ }")));

    verify(mapper, never()).insertConfirmedUsage(
        any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    verify(mapper).insertCalculationOutcome(
        any(), eq("sol-card"), eq("sol-approval"), eq("sol-basic"), eq("sol-rule"),
        eq(null), any(), eq("POINT"), eq(BigDecimal.ZERO), eq(BigDecimal.ZERO),
        eq(BigDecimal.ZERO), eq("not_applied"), eq("CALCULATION_UNSUPPORTED"));
  }

  private void prepareSolApproval(
      LocalDateTime approvedAt, int amount, int previousSpend, String merchantName,
      SimpleBenefitRuleRow rule) {
    when(mapper.findApprovalsForCalculation(List.of("sol-approval")))
        .thenReturn(List.of(new BenefitApprovalRow(
            "sol-approval", "sol-card", amount, approvedAt, null)));
    when(mapper.findPreviousMonthSpend("sol-card", "2026-07")).thenReturn(previousSpend);
    when(mapper.findApprovalMerchantNormalizedName("sol-approval")).thenReturn(merchantName);
    when(mapper.findSimpleRulesForUserCard(eq("sol-card"), any())).thenReturn(List.of(rule));
  }

  private SimpleBenefitRuleRow solSpecialRule() {
    return new SimpleBenefitRuleRow(
        "sol-rule", "sol-special", "특별 적립 (주유/쇼핑/배달)",
        "points", "point", new BigDecimal("2.5"), null, new BigDecimal("400000"),
        null, "all_merchants", "ALL", 1, "include", null, null, "PARTIAL");
  }

  private SimpleBenefitRuleRow solBasicRule() {
    return new SimpleBenefitRuleRow(
        "sol-rule", "sol-basic", "국내/외 전가맹점 기본 적립", "points", "point",
        BigDecimal.ONE, null, new BigDecimal("400000"), null, "all_merchants", "ALL", 1,
        "include", null, null, "PARTIAL");
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

  private String jsonDefinitionWithoutPerformance() {
    return """
        {
          "schemaVersion":1,
          "conditions":{"all":[],"any":[],"none":[]},
          "reward":{
            "benefitType":"POINT",
            "rewardUnit":"POINT",
            "calculation":"RATE",
            "rate":"0.05"
          },
          "limits":[]
        }
        """;
  }

  private String deepDreamDefinition() {
    return """
        {"schemaVersion":1,"conditions":{"all":[],"any":[],"none":[]},
         "reward":{"benefitType":"POINT","rewardUnit":"POINT","calculation":"RATE",
                   "rate":"0.002","value":"0","spendUnitAmount":"0"},"limits":[]}
        """;
  }
}
