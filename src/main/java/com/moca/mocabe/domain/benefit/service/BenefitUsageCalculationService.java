package com.moca.mocabe.domain.benefit.service;

import com.moca.mocabe.domain.benefit.calculation.BasicBenefitCalculator;
import com.moca.mocabe.domain.benefit.calculation.BenefitCalculator;
import com.moca.mocabe.domain.benefit.calculation.BenefitRuleTargetEvaluator;
import com.moca.mocabe.domain.benefit.mapper.BenefitCalculationMapper;
import com.moca.mocabe.domain.benefit.model.BenefitApprovalRow;
import com.moca.mocabe.domain.benefit.model.BenefitAreaSpendRow;
import com.moca.mocabe.domain.benefit.model.BenefitCalculationContext;
import com.moca.mocabe.domain.benefit.model.BenefitCalculationResult;
import com.moca.mocabe.domain.benefit.model.BenefitLimitTierSelection;
import com.moca.mocabe.domain.benefit.model.BenefitRule;
import com.moca.mocabe.domain.benefit.model.BenefitRuleTarget;
import com.moca.mocabe.domain.benefit.model.BenefitUsageCounts;
import com.moca.mocabe.domain.benefit.model.MonthlyBenefitLimit;
import com.moca.mocabe.domain.benefit.model.SimpleBenefitRuleRow;
import com.moca.mocabe.domain.benefit.rule.BenefitRuleDefinition;
import com.moca.mocabe.domain.benefit.rule.BenefitRuleDefinitionParser;
import com.moca.mocabe.domain.benefit.rule.JsonBenefitRuleEvaluator;
import com.moca.mocabe.domain.benefit.type.BenefitBasis;
import com.moca.mocabe.domain.benefit.type.BenefitPromotionCondition;
import com.moca.mocabe.domain.benefit.type.BenefitTargetMatchMode;
import com.moca.mocabe.domain.benefit.type.BenefitType;
import com.moca.mocabe.domain.benefit.type.RewardUnit;
import com.moca.mocabe.domain.codef.model.ApprovalInsert;
import com.moca.mocabe.global.exception.benefit.InvalidBenefitRecalculationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신규 CODEF 승인에 대해 구조화된 혜택 룰을 계산하고 확정 사용 이력을 적재한다. JSON 룰은 CODEF 승인과 내부 원장으로 확인 가능한 조건만 평가하며,
 * 확인할 수 없는 조건은 적용으로 추정하지 않는다.
 */
public class BenefitUsageCalculationService {
  private static final String SOL_BASIC_OFFER = "국내/외 전가맹점 기본 적립";
  private static final String SOL_SPECIAL_OFFER = "특별 적립 (주유/쇼핑/배달)";
  private static final BigDecimal SOL_FUEL_MONTHLY_SPEND_LIMIT = new BigDecimal("300000");
  private static final Set<String> SOL_UNSUPPORTED_MERCHANTS = Set.of(
      "쿠팡", "SSG.COM", "무신사", "29CM", "땡겨요", "배달의민족", "요기요", "쿠팡이츠",
      "넷플릭스", "유튜브 프리미엄", "티빙", "디즈니플러스", "네이버플러스 멤버십",
      "쿠팡 와우 멤버십");
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final DateTimeFormatter YEAR_MONTH =
      DateTimeFormatter.ofPattern("uuuu-MM").withResolverStyle(ResolverStyle.STRICT);
  private final BenefitCalculationMapper mapper;
  private final BenefitCalculator calculator = new BasicBenefitCalculator();
  private final BenefitRuleTargetEvaluator targetEvaluator = new BenefitRuleTargetEvaluator();
  private final BenefitRuleDefinitionParser definitionParser = new BenefitRuleDefinitionParser();
  private final JsonBenefitRuleEvaluator jsonRuleEvaluator = new JsonBenefitRuleEvaluator();
  private final BenefitLimitTierSelector tierSelector = new BenefitLimitTierSelector();
  private final BenefitAreaSpendService benefitAreaSpendService;

  public BenefitUsageCalculationService(BenefitCalculationMapper mapper) {
    this.mapper = mapper;
    this.benefitAreaSpendService = new BenefitAreaSpendService(mapper);
  }

  /** 기존 단위 테스트용 CardSyncService 생성자에서 사용하는 부작용 없는 구현이다. */
  public static BenefitUsageCalculationService noop() {
    return new BenefitUsageCalculationService(null);
  }

  public boolean isEnabled() {
    return mapper != null;
  }

  @Transactional
  public void calculateAndPersist(List<ApprovalInsert> insertedApprovals) {
    if (mapper == null || insertedApprovals == null || insertedApprovals.isEmpty()) {
      return;
    }
    recalculateFullMonths(insertedApprovals.stream().map(ApprovalInsert::approvalId).toList());
  }

  /** 승인 동기화 전에 이미 저장된 승인까지 같은 계산 경로로 보강한다. */
  @Transactional
  public void calculateAndPersistForPeriod(
      String userId, LocalDateTime fromUtc, LocalDateTime toUtc) {
    if (mapper == null) {
      return;
    }
    List<String> approvalIds = mapper.findApprovalIdsForPeriod(userId, fromUtc, toUtc);
    if (approvalIds == null || approvalIds.isEmpty()) {
      return;
    }
    recalculateFullMonths(approvalIds);
  }

  /** 인증 사용자의 지정 월 승인 원본을 보존한 채 혜택 결과만 재계산한다. */
  @Transactional
  public String recalculateForMonth(String userId, String requestedYearMonth) {
    YearMonth month = parseRecalculationMonth(requestedYearMonth);
    LocalDateTime fromUtc = toUtc(month);
    LocalDateTime toUtc = toUtc(month.plusMonths(1));
    calculateAndPersistForPeriod(userId, fromUtc, toUtc);
    return month.toString();
  }

  private YearMonth parseRecalculationMonth(String requestedYearMonth) {
    if (requestedYearMonth == null || requestedYearMonth.isBlank()) {
      return YearMonth.now(SEOUL);
    }
    try {
      return YearMonth.parse(requestedYearMonth, YEAR_MONTH);
    } catch (DateTimeParseException exception) {
      throw new InvalidBenefitRecalculationException("yearMonth는 YYYY-MM 형식이어야 합니다.");
    }
  }

  private LocalDateTime toUtc(YearMonth month) {
    return month.atDay(1).atStartOfDay(SEOUL)
        .withZoneSameInstant(java.time.ZoneOffset.UTC)
        .toLocalDateTime();
  }

  private void recalculateFullMonths(List<String> approvalIds) {
    List<BenefitApprovalRow> requestedApprovals = mapper.findApprovalsForCalculation(approvalIds);
    Set<String> fullMonthIds = new java.util.LinkedHashSet<>(approvalIds);
    Set<String> requestedCardMonths = new java.util.LinkedHashSet<>();
    Set<String> requestedCards = new java.util.LinkedHashSet<>();
    for (BenefitApprovalRow approval : requestedApprovals) {
      requestedCards.add(approval.userCardId());
      LocalDate usageDate = toUsageDate(approval);
      if (mapper.hasBenefitOfferForUserCard(
          approval.userCardId(), "Deep Dream 모두드림 0.2%")) {
        requestedCardMonths.add(
            approval.userCardId() + "\t" + YearMonth.from(usageDate));
      }
    }
    for (String userCardId : requestedCards) {
      mapper.lockUserCardForBenefitCalculation(userCardId);
    }
    for (String cardMonth : requestedCardMonths) {
      String[] key = cardMonth.split("\t", 2);
      List<String> monthlyIds = mapper.findApprovedApprovalIdsForCardMonth(key[0], key[1]);
      if (monthlyIds != null) {
        fullMonthIds.addAll(monthlyIds);
      }
    }
    List<String> recalculationIds = List.copyOf(fullMonthIds);
    mapper.deleteCalculationOutcomes(recalculationIds);
    mapper.deleteBenefitUsages(recalculationIds);
    if (recalculationIds.size() == approvalIds.size()) {
      calculateApprovals(requestedApprovals);
    } else {
      calculateApprovalIds(recalculationIds);
    }
  }

  private void calculateApprovalIds(List<String> approvalIds) {
    calculateApprovals(mapper.findApprovalsForCalculation(approvalIds));
  }

  private void calculateApprovals(List<BenefitApprovalRow> approvals) {
    // 월 최다 영역은 승인 순서가 아니라 월 전체 이용액으로 정한다. 계산 전에 영역 원장을 먼저 완성한다.
    Set<String> cardMonths = new java.util.LinkedHashSet<>();
    for (BenefitApprovalRow approval : approvals) {
      LocalDate usageDate = toUsageDate(approval);
      String usageMonth = YearMonth.from(usageDate).toString();
      benefitAreaSpendService.recordApproval(
          approval.approvalId(), approval.userCardId(), BigDecimal.valueOf(approval.amount()),
          YearMonth.from(usageDate));
      cardMonths.add(approval.userCardId() + "\t" + usageMonth);
    }
    // 재동기화 후에도 집계는 승인 이벤트 원장과 정확히 같아야 한다.
    for (String cardMonth : cardMonths) {
      String[] key = cardMonth.split("\t", 2);
      mapper.rebuildMonthlyBenefitAreaSpends(key[0], key[1]);
    }
    for (BenefitApprovalRow approval : approvals) {
      calculateApproval(approval);
    }
  }

  private void calculateApproval(BenefitApprovalRow approval) {
    LocalDate usageDate = toUsageDate(approval);
    Integer previousMonthSpendSnapshot =
        mapper.findPreviousMonthSpend(
            approval.userCardId(),
            YearMonth.from(usageDate).minusMonths(1).format(YEAR_MONTH));
    BigDecimal previousMonthSpend = BigDecimal.valueOf(valueOrZero(previousMonthSpendSnapshot));
    Integer currentMonthSpendSnapshot =
        mapper.findCurrentMonthSpend(
            approval.userCardId(), YearMonth.from(usageDate).format(YEAR_MONTH));
    BigDecimal currentMonthSpend = BigDecimal.valueOf(valueOrZero(currentMonthSpendSnapshot));
    String merchantName = mapper.findApprovalMerchantNormalizedName(approval.approvalId());
    Map<String, List<SimpleBenefitRuleRow>> rowsByRule = new LinkedHashMap<>();
    for (SimpleBenefitRuleRow row :
        mapper.findSimpleRulesForUserCard(approval.userCardId(), usageDate)) {
      rowsByRule.computeIfAbsent(row.ruleId(), ignored -> new java.util.ArrayList<>()).add(row);
    }
    for (List<SimpleBenefitRuleRow> rows : rowsByRule.values()) {
      SimpleBenefitRuleRow first = rows.get(0);
      BenefitLimitTierSelection tierSelection =
          tierSelector.select(
              mapper.findMonthlyRewardLimitCandidates(first.offerId(), usageDate, limitUnitFor(first)),
              previousMonthSpend,
              currentMonthSpend,
              usageDate);
      MonthlyBenefitLimit monthlyLimit = tierSelection.limit();
      BigDecimal usedMonthlyValue =
          monthlyLimit == null
              ? BigDecimal.ZERO
              : mapper
                  .findConfirmedMonthlyRewardsForUpdate(
                      approval.userCardId(),
                      monthlyLimit.limitPolicyId(),
                      monthlyLimit.sharedGroupKey(),
                      usageDate.withDayOfMonth(1),
                      usageDate.withDayOfMonth(1).plusMonths(1),
                      limitUnitFor(first))
                  .stream()
                  .reduce(BigDecimal.ZERO, BigDecimal::add);
      LocalDate usageMonthStart = usageDate.withDayOfMonth(1);
      BenefitUsageCounts usageCounts =
          mapper.findConfirmedUsageCounts(
              approval.userCardId(),
              first.offerId(),
              usageDate,
              usageMonthStart,
              usageMonthStart.plusMonths(1));
      if (usageCounts == null) {
        usageCounts = new BenefitUsageCounts(0, 0);
      }
      BenefitCalculationContext context =
          new BenefitCalculationContext(
              BigDecimal.valueOf(approval.amount()),
              BigDecimal.ONE,
              previousMonthSpend,
              approval.approvedAt(),
              approval.merchantCategoryCode(),
              false,
              usageCounts.dailyCount(),
              usageCounts.monthlyCount(),
              true,
              true,
              false,
              targetAttributes(approval, previousMonthSpendSnapshot != null));
      BenefitCalculationResult result =
          calculate(
              rows,
              context,
              monthlyLimit == null ? BigDecimal.ZERO : monthlyLimit.limitValue(),
              usedMonthlyValue);
      if (tierSelection.status() == BenefitLimitTierSelection.Status.PERFORMANCE_NOT_MET) {
        result = performanceNotMet(result);
      }
      BigDecimal eligibleAmount = BigDecimal.valueOf(approval.amount());
      if (SOL_SPECIAL_OFFER.equals(first.offerName()) && result.applicable()) {
        BigDecimal usedEligibleSpend = zero(mapper.findConfirmedMonthlyEligibleSpendForUpdate(
            approval.userCardId(), first.offerId(), usageMonthStart,
            usageMonthStart.plusMonths(1)));
        eligibleAmount = SOL_FUEL_MONTHLY_SPEND_LIMIT.subtract(usedEligibleSpend)
            .max(BigDecimal.ZERO).min(eligibleAmount);
        result = calculateSolFuelReward(
            first, previousMonthSpend, eligibleAmount, monthlyLimit, usedMonthlyValue);
      }
      result = unsupportedSolSpecialMerchant(first, merchantName, result);
      result = capMonthlyOfferReward(
          approval, usageDate, usageMonthStart, previousMonthSpend, first, result);
      result = applyDeepDreamAreaRate(approval, usageDate, first, previousMonthSpend, result);
      persistOutcome(approval, usageDate, first, monthlyLimit, result);
      if (result.applicable() && result.appliedRewardValue().signum() > 0) {
        persist(approval, usageDate, first, monthlyLimit, eligibleAmount, result);
      }
    }
  }

  private BenefitCalculationResult calculateSolFuelReward(
      SimpleBenefitRuleRow rule,
      BigDecimal previousMonthSpend,
      BigDecimal eligibleAmount,
      MonthlyBenefitLimit monthlyLimit,
      BigDecimal usedMonthlyValue) {
    BigDecimal rate = previousMonthSpend.compareTo(new BigDecimal("1000000")) >= 0
        ? new BigDecimal("0.05") : new BigDecimal("0.025");
    BigDecimal raw = eligibleAmount.multiply(rate).setScale(0, java.math.RoundingMode.FLOOR);
    BigDecimal remainingReward = monthlyLimit == null
        ? raw : monthlyLimit.limitValue().subtract(zero(usedMonthlyValue)).max(BigDecimal.ZERO);
    BigDecimal applied = raw.min(remainingReward);
    boolean applicable = eligibleAmount.signum() > 0 && applied.signum() > 0;
    return new BenefitCalculationResult(
        rule.ruleId(), BenefitType.POINT, RewardUnit.POINT, applicable, raw, applied,
        remainingReward.subtract(applied).max(BigDecimal.ZERO),
        applicable
            ? com.moca.mocabe.domain.benefit.type.BenefitRejectionReason.NONE
            : com.moca.mocabe.domain.benefit.type.BenefitRejectionReason.MONTHLY_LIMIT_EXHAUSTED);
  }

  private BenefitCalculationResult unsupportedSolSpecialMerchant(
      SimpleBenefitRuleRow rule, String merchantName, BenefitCalculationResult result) {
    if ((!SOL_BASIC_OFFER.equals(rule.offerName()) && !SOL_SPECIAL_OFFER.equals(rule.offerName()))
        || merchantName == null || !SOL_UNSUPPORTED_MERCHANTS.contains(merchantName)) {
      return result;
    }
    return new BenefitCalculationResult(
        result.ruleId(), BenefitType.POINT, RewardUnit.POINT, false,
        BigDecimal.ZERO, BigDecimal.ZERO, result.remainingLimitValue(),
        com.moca.mocabe.domain.benefit.type.BenefitRejectionReason.CALCULATION_UNSUPPORTED);
  }

  private BenefitCalculationResult capMonthlyOfferReward(
      BenefitApprovalRow approval,
      LocalDate usageDate,
      LocalDate usageMonthStart,
      BigDecimal previousMonthSpend,
      SimpleBenefitRuleRow rule,
      BenefitCalculationResult result) {
    BigDecimal offerLimit = mapper.findMonthlyOfferRewardLimit(
        rule.offerId(), usageDate, previousMonthSpend, limitUnitFor(rule));
    if (offerLimit == null || !result.applicable()) {
      return result;
    }
    BigDecimal used = zero(mapper.findConfirmedMonthlyRewardForOfferForUpdate(
        approval.userCardId(), rule.offerId(), usageMonthStart, usageMonthStart.plusMonths(1)));
    BigDecimal remaining = offerLimit.subtract(used).max(BigDecimal.ZERO);
    BigDecimal applied = result.appliedRewardValue().min(remaining);
    boolean applicable = applied.signum() > 0;
    return new BenefitCalculationResult(
        result.ruleId(), result.benefitType(), result.rewardUnit(), applicable,
        result.rawRewardValue(), applied,
        result.remainingLimitValue().min(remaining.subtract(applied).max(BigDecimal.ZERO)),
        applicable
            ? result.rejectionReason()
            : com.moca.mocabe.domain.benefit.type.BenefitRejectionReason.MONTHLY_LIMIT_EXHAUSTED);
  }

  private BenefitCalculationResult applyDeepDreamAreaRate(
      BenefitApprovalRow approval,
      LocalDate usageDate,
      SimpleBenefitRuleRow rule,
      BigDecimal previousMonthSpend,
      BenefitCalculationResult result) {
    if (!"Deep Dream 모두드림 0.2%".equals(rule.offerName())
        || result.rewardUnit() != RewardUnit.POINT
        || !result.applicable()) {
      return result;
    }
    List<String> areaKeys = benefitAreaSpendService.findAreaKeysForApproval(
        approval.approvalId(), "DREAM");
    if (areaKeys.isEmpty()) {
      return result;
    }
    BenefitAreaSpendRow topArea = benefitAreaSpendService.findTopArea(
        approval.userCardId(), "DREAM", YearMonth.from(usageDate));
    boolean topAreaApproval = topArea != null && areaKeys.contains(topArea.areaKey());
    LocalDate monthStart = usageDate.withDayOfMonth(1);
    BigDecimal usedExtra = mapper.findConfirmedDeepDreamExtraRewardForUpdate(
        approval.userCardId(), monthStart, monthStart.plusMonths(1));
    BenefitAreaRewardCalculator.RewardAllocation allocation =
        new BenefitAreaRewardCalculator().allocate(BigDecimal.valueOf(approval.amount()),
            previousMonthSpend, true, topAreaApproval, usedExtra);
    return new BenefitCalculationResult(
        result.ruleId(), result.benefitType(), result.rewardUnit(), true,
        allocation.rawReward(), allocation.appliedReward(), allocation.remainingExtraLimit(),
        result.rejectionReason());
  }

  private LocalDate toUsageDate(BenefitApprovalRow approval) {
    return approval.approvedAt().atZone(java.time.ZoneOffset.UTC)
        .withZoneSameInstant(SEOUL).toLocalDate();
  }

  private BenefitCalculationResult performanceNotMet(BenefitCalculationResult calculated) {
    return new BenefitCalculationResult(
        calculated.ruleId(),
        calculated.benefitType(),
        calculated.rewardUnit(),
        false,
        calculated.rawRewardValue(),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        com.moca.mocabe.domain.benefit.type.BenefitRejectionReason.PERFORMANCE_NOT_MET);
  }


  private BenefitCalculationResult calculate(
      List<SimpleBenefitRuleRow> rows,
      BenefitCalculationContext context,
      BigDecimal monthlyLimitValue,
      BigDecimal usedMonthlyValue) {
    SimpleBenefitRuleRow first = rows.get(0);
    if (first.ruleDefinitionJson() == null || first.ruleDefinitionJson().isBlank()) {
      return calculator.calculate(toRule(rows, monthlyLimitValue, usedMonthlyValue), context);
    }
    Set<BenefitRuleTarget> targets =
        rows.stream().map(this::toTarget).collect(java.util.stream.Collectors.toUnmodifiableSet());
    if (!targetEvaluator.matches(targets, context)) {
      return rejected(
          first,
          monthlyLimitValue,
          usedMonthlyValue,
          com.moca.mocabe.domain.benefit.type.BenefitRejectionReason.TARGET_NOT_MATCHED);
    }
    try {
      BenefitRuleDefinition definition = definitionParser.parse(first.ruleDefinitionJson());
      return jsonRuleEvaluator.evaluate(
          first.ruleId(), definition, context, monthlyLimitValue, usedMonthlyValue);
    } catch (IllegalArgumentException exception) {
      return rejected(
          first,
          monthlyLimitValue,
          usedMonthlyValue,
          com.moca.mocabe.domain.benefit.type.BenefitRejectionReason.RULE_DATA_UNAVAILABLE);
    }
  }

  private BenefitCalculationResult rejected(
      SimpleBenefitRuleRow row,
      BigDecimal monthlyLimitValue,
      BigDecimal usedMonthlyValue,
      com.moca.mocabe.domain.benefit.type.BenefitRejectionReason reason) {
    RewardUnit unit = rewardUnit(row.rewardUnit());
    return new BenefitCalculationResult(
        row.ruleId(),
        benefitType(row.rewardType(), unit),
        unit,
        false,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        zero(monthlyLimitValue).subtract(zero(usedMonthlyValue)).max(BigDecimal.ZERO),
        reason);
  }

  private BenefitRule toRule(
      List<SimpleBenefitRuleRow> rows, BigDecimal monthlyLimitValue, BigDecimal usedMonthlyValue) {
    SimpleBenefitRuleRow first = rows.get(0);
    String unit = normalized(first.rewardUnit());
    RewardUnit rewardUnit = rewardUnit(unit);
    BenefitBasis basis =
        "PERCENT".equals(unit)
            ? BenefitBasis.RATE
            : first.rewardBasisAmount() != null && first.rewardBasisAmount().signum() > 0
                ? BenefitBasis.PER_SPEND_UNIT
                : BenefitBasis.FIXED;
    BigDecimal value = zero(first.rewardValue());
    return new BenefitRule(
        first.ruleId(),
        benefitType(first.rewardType(), rewardUnit),
        basis,
        rewardUnit,
        basis == BenefitBasis.RATE ? value.movePointLeft(2) : BigDecimal.ZERO,
        basis == BenefitBasis.RATE ? BigDecimal.ZERO : value,
        basis == BenefitBasis.PER_SPEND_UNIT ? first.rewardBasisAmount() : BigDecimal.ZERO,
        zero(first.transactionMaxKrw()),
        zero(first.transactionMinKrw()),
        zero(first.previousSpendMinKrw()),
        monthlyLimitValue,
        usedMonthlyValue,
        BenefitPromotionCondition.NONE,
        Set.of(),
        0,
        0,
        false,
        false,
        rows.stream().map(this::toTarget).collect(java.util.stream.Collectors.toSet()),
        Set.of());
  }

  private BenefitRuleTarget toTarget(SimpleBenefitRuleRow row) {
    return new BenefitRuleTarget(
        row.conditionGroup(), BenefitTargetMatchMode.valueOf(row.matchMode().toUpperCase(Locale.ROOT)),
        row.targetType(), row.targetCode());
  }

  private Map<String, Set<String>> targetAttributes(
      BenefitApprovalRow approval,
      boolean previousMonthSpendAvailable) {
    Map<String, Set<String>> attributes = new LinkedHashMap<>();
    java.util.LinkedHashSet<String> availableFields =
        new java.util.LinkedHashSet<>(
            Set.of(
                "PAYMENT_AMOUNT",
                "APPROVED_AT",
                "USED_DAILY_COUNT",
                "USED_MONTHLY_COUNT",
                "FOREIGN_TRANSACTION"));
    if (previousMonthSpendAvailable) {
      availableFields.add("PREVIOUS_MONTH_SPEND");
    }
    if (approval.merchantId() != null && !approval.merchantId().isBlank()) {
      attributes.put("merchant", Set.of(approval.merchantId()));
      availableFields.add("MERCHANT");
    }
    if (approval.merchantCategoryCodes() != null
        && !approval.merchantCategoryCodes().isBlank()) {
      attributes.put(
          "merchant_category_code",
          splitValues(approval.merchantCategoryCodes()));
      availableFields.add("MERCHANT_CATEGORY");
    }
    if (approval.merchantCategoryIds() != null && !approval.merchantCategoryIds().isBlank()) {
      attributes.put("merchant_category", splitValues(approval.merchantCategoryIds()));
    }
    attributes.put("available_field", Set.copyOf(availableFields));
    return Map.copyOf(attributes);
  }

  private Set<String> splitValues(String values) {
    return java.util.Arrays.stream(values.split(","))
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private void persist(
      BenefitApprovalRow approval,
      LocalDate usageDate,
      SimpleBenefitRuleRow rule,
      MonthlyBenefitLimit monthlyLimit,
      BigDecimal eligibleAmount,
      BenefitCalculationResult result) {
    boolean monetary = result.rewardUnit() == RewardUnit.KRW;
    mapper.insertConfirmedUsage(
        UUID.randomUUID().toString(),
        approval.userCardId(),
        rule.offerId(),
        rule.ruleId(),
        monthlyLimit == null ? null : monthlyLimit.limitPolicyId(),
        approval.approvalId(),
        usageDate,
        eligibleAmount,
        monetary ? result.appliedRewardValue() : BigDecimal.ZERO,
        monetary ? null : result.appliedRewardValue(),
        monetary ? null : result.rewardUnit().name().toLowerCase(Locale.ROOT),
        approval.approvedAt());
  }

  /**
   * 실제 계산된 예상액과 적용액을 함께 보존한다. 조건 미충족은 0원 outcome과 사유를 남겨 계산 보류·미적용을 구분하고, 금액이 있는 missed 집계에는 예상액과
   * 적용액의 차이만 사용한다.
   */
  private void persistOutcome(
      BenefitApprovalRow approval,
      LocalDate usageDate,
      SimpleBenefitRuleRow rule,
      MonthlyBenefitLimit monthlyLimit,
      BenefitCalculationResult result) {
    BigDecimal expected = zero(result.rawRewardValue());
    BigDecimal applied = zero(result.appliedRewardValue());
    BigDecimal missed = expected.subtract(applied).max(BigDecimal.ZERO);
    if (expected.signum() <= 0 && result.applicable()) {
      return;
    }
    String status =
        !result.applicable() || applied.signum() == 0
            ? "not_applied"
            : missed.signum() == 0 ? "applied" : "partially_applied";
    mapper.insertCalculationOutcome(
        UUID.randomUUID().toString(),
        approval.userCardId(),
        approval.approvalId(),
        rule.offerId(),
        rule.ruleId(),
        monthlyLimit == null ? null : monthlyLimit.limitPolicyId(),
        usageDate,
        result.rewardUnit().name(),
        expected,
        applied,
        missed,
        status,
        result.rejectionReason().name());
  }

  private BenefitType benefitType(String rewardType, RewardUnit rewardUnit) {
    if (rewardUnit == RewardUnit.MILE) {
      return BenefitType.MILEAGE;
    }
    if ("CASHBACK".equals(normalized(rewardType))) {
      return BenefitType.CASHBACK;
    }
    if ("POINTS".equals(normalized(rewardType))) {
      return BenefitType.POINT;
    }
    return BenefitType.DISCOUNT;
  }

  private int valueOrZero(Integer value) {
    return value == null ? 0 : value;
  }

  private RewardUnit rewardUnit(String value) {
    String unit = normalized(value);
    return "POINT".equals(unit)
        ? RewardUnit.POINT
        : "MILE".equals(unit) ? RewardUnit.MILE : RewardUnit.KRW;
  }

  private BigDecimal zero(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private String normalized(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  private String limitUnitFor(SimpleBenefitRuleRow rule) {
    String rewardUnit = normalized(rule.rewardUnit());
    return "POINT".equals(rewardUnit) ? "point" : "MILE".equals(rewardUnit) ? "mile" : "KRW";
  }
}
