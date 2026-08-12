package com.moca.mocabe.domain.benefit.service;

import com.moca.mocabe.domain.benefit.calculation.BasicBenefitCalculator;
import com.moca.mocabe.domain.benefit.calculation.BenefitCalculator;
import com.moca.mocabe.domain.benefit.mapper.BenefitCalculationMapper;
import com.moca.mocabe.domain.benefit.model.BenefitApprovalRow;
import com.moca.mocabe.domain.benefit.model.BenefitCalculationContext;
import com.moca.mocabe.domain.benefit.model.BenefitCalculationResult;
import com.moca.mocabe.domain.benefit.model.BenefitRule;
import com.moca.mocabe.domain.benefit.model.BenefitRuleTarget;
import com.moca.mocabe.domain.benefit.model.MonthlyBenefitLimit;
import com.moca.mocabe.domain.benefit.model.SimpleBenefitRuleRow;
import com.moca.mocabe.domain.benefit.type.BenefitBasis;
import com.moca.mocabe.domain.benefit.type.BenefitPromotionCondition;
import com.moca.mocabe.domain.benefit.type.BenefitTargetMatchMode;
import com.moca.mocabe.domain.benefit.type.BenefitType;
import com.moca.mocabe.domain.benefit.type.RewardUnit;
import com.moca.mocabe.domain.codef.model.ApprovalInsert;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신규 CODEF 승인에 대해 이미 구조화된 "단순" 혜택 룰을 계산하고 확정 사용 이력을 적재한다. 복합 대상, 선택 옵션, 시간 조건, 공유 한도 룰은 mapper에서
 * 제외한다. 원문 구조화가 진행 중인 상태에서 추정 혜택을 확정 이력으로 만들지 않기 위한 안전 경계다.
 */
public class BenefitUsageCalculationService {
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("uuuu-MM");
  private final BenefitCalculationMapper mapper;
  private final BenefitCalculator calculator = new BasicBenefitCalculator();

  public BenefitUsageCalculationService(BenefitCalculationMapper mapper) {
    this.mapper = mapper;
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
    List<BenefitApprovalRow> approvals =
        mapper.findApprovalsForCalculation(
            insertedApprovals.stream().map(ApprovalInsert::approvalId).toList());
    for (BenefitApprovalRow approval : approvals) {
      calculateApproval(approval);
    }
  }

  private void calculateApproval(BenefitApprovalRow approval) {
    // 사용 이력 합계를 읽고 새 이력을 넣는 동안 같은 카드의 계산은 하나만 실행한다.
    // shared_group_key는 카드 상품 내 공유 한도이므로 user_card 단위 직렬화가 충분하다.
    mapper.lockUserCardForBenefitCalculation(approval.userCardId());
    LocalDate usageDate =
        approval
            .approvedAt()
            .atZone(java.time.ZoneOffset.UTC)
            .withZoneSameInstant(SEOUL)
            .toLocalDate();
    BigDecimal previousMonthSpend =
        BigDecimal.valueOf(
            valueOrZero(
                mapper.findPreviousMonthSpend(
                    approval.userCardId(),
                    YearMonth.from(usageDate).minusMonths(1).format(YEAR_MONTH))));
    Map<String, List<SimpleBenefitRuleRow>> rowsByRule = new LinkedHashMap<>();
    for (SimpleBenefitRuleRow row :
        mapper.findSimpleRulesForUserCard(approval.userCardId(), usageDate)) {
      rowsByRule.computeIfAbsent(row.ruleId(), ignored -> new java.util.ArrayList<>()).add(row);
    }
    for (List<SimpleBenefitRuleRow> rows : rowsByRule.values()) {
      SimpleBenefitRuleRow first = rows.get(0);
      MonthlyBenefitLimit monthlyLimit =
          mapper.findApplicableMonthlyRewardLimit(
              first.offerId(), usageDate, previousMonthSpend, limitUnitFor(first));
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
      BenefitRule rule =
          toRule(
              rows,
              monthlyLimit == null ? BigDecimal.ZERO : monthlyLimit.limitValue(),
              usedMonthlyValue);
      BenefitCalculationResult result =
          calculator.calculate(
              rule,
              new BenefitCalculationContext(
                  BigDecimal.valueOf(approval.amount()),
                  BigDecimal.ONE,
                  previousMonthSpend,
                  approval.approvedAt(),
                  approval.merchantCategoryCode(),
                  false,
                  0,
                  0,
                  true,
                  true,
                  false,
                  targetAttributes(approval)));
      persistOutcome(approval, usageDate, first, monthlyLimit, result);
      if (result.applicable() && result.appliedRewardValue().signum() > 0) {
        persist(approval, usageDate, first, monthlyLimit, result);
      }
    }
  }

  private BenefitRule toRule(
      List<SimpleBenefitRuleRow> rows, BigDecimal monthlyLimitValue, BigDecimal usedMonthlyValue) {
    SimpleBenefitRuleRow first = rows.get(0);
    String unit = normalized(first.rewardUnit());
    RewardUnit rewardUnit =
        "POINT".equals(unit)
            ? RewardUnit.POINT
            : "MILE".equals(unit) ? RewardUnit.MILE : RewardUnit.KRW;
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
        BigDecimal.ZERO,
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

  private Map<String, Set<String>> targetAttributes(BenefitApprovalRow approval) {
    Map<String, Set<String>> attributes = new LinkedHashMap<>();
    if (approval.merchantId() != null && !approval.merchantId().isBlank()) {
      attributes.put("merchant", Set.of(approval.merchantId()));
    }
    if (approval.merchantCategoryIds() != null && !approval.merchantCategoryIds().isBlank()) {
      attributes.put("merchant_category",
          java.util.Arrays.stream(approval.merchantCategoryIds().split(","))
              .map(String::trim).filter(value -> !value.isEmpty())
              .collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }
    return Map.copyOf(attributes);
  }

  private void persist(
      BenefitApprovalRow approval,
      LocalDate usageDate,
      SimpleBenefitRuleRow rule,
      MonthlyBenefitLimit monthlyLimit,
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
        BigDecimal.valueOf(approval.amount()),
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
