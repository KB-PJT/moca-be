package com.moca.mocabe.domain.benefit.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moca.mocabe.domain.benefit.model.BenefitCalculationContext;
import com.moca.mocabe.domain.benefit.model.BenefitCalculationResult;
import com.moca.mocabe.domain.benefit.model.BenefitRule;
import com.moca.mocabe.domain.benefit.model.BenefitRuleSchedule;
import com.moca.mocabe.domain.benefit.model.BenefitRuleTarget;
import com.moca.mocabe.domain.benefit.type.BenefitBasis;
import com.moca.mocabe.domain.benefit.type.BenefitPromotionCondition;
import com.moca.mocabe.domain.benefit.type.BenefitRejectionReason;
import com.moca.mocabe.domain.benefit.type.BenefitTargetMatchMode;
import com.moca.mocabe.domain.benefit.type.BenefitType;
import com.moca.mocabe.domain.benefit.type.RewardUnit;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("구조화 카드 혜택 적용 조건")
class StructuredBenefitEligibilityTest {

  private final BenefitCalculator calculator = new BasicBenefitCalculator();

  @Test
  @DisplayName("컴포즈 오프라인 또는 공식 앱 결제 조건을 그룹 OR로 평가한다")
  void matchesAnyIncludeConditionGroup() {
    Set<BenefitRuleTarget> targets =
        Set.of(
            include(1, "merchant", "COMPOSE"),
            include(1, "channel", "OFFLINE"),
            include(2, "merchant", "COMPOSE"),
            include(2, "entry_method", "OFFICIAL_APP"));

    BenefitCalculationResult result =
        calculator.calculate(
            rule(targets, Set.of()),
            context(
                false,
                "2026-08-08T12:00:00",
                Map.of("merchant", Set.of("COMPOSE"), "entry_method", Set.of("OFFICIAL_APP"))));

    assertTrue(result.applicable());
  }

  @Test
  @DisplayName("include 그룹 일부만 맞으면 대상이 아니다")
  void requiresEveryIncludeInMatchedGroup() {
    Set<BenefitRuleTarget> targets =
        Set.of(include(1, "merchant", "COMPOSE"), include(1, "channel", "OFFLINE"));

    BenefitCalculationResult result =
        calculator.calculate(
            rule(targets, Set.of()),
            context(false, "2026-08-08T12:00:00", Map.of("merchant", Set.of("COMPOSE"))));

    assertFalse(result.applicable());
    assertEquals(BenefitRejectionReason.TARGET_NOT_MATCHED, result.rejectionReason());
  }

  @Test
  @DisplayName("상품권 exclude는 include가 맞아도 최종 적용에서 제외한다")
  void exclusionOverridesIncludedGroup() {
    Set<BenefitRuleTarget> targets =
        Set.of(include(1, "merchant_category", "CAFE"), exclude(1, "product", "GIFT_CARD"));

    BenefitCalculationResult result =
        calculator.calculate(
            rule(targets, Set.of()),
            context(false, "2026-08-08T12:00:00", Map.of("product", Set.of("GIFT_CARD"))));

    assertFalse(result.applicable());
    assertEquals(BenefitRejectionReason.TARGET_NOT_MATCHED, result.rejectionReason());
  }

  @Test
  @DisplayName("21시부터 다음 날 9시 전까지인 자정 통과 일정을 적용한다")
  void matchesCrossMidnightSchedule() {
    BenefitRuleSchedule schedule =
        new BenefitRuleSchedule(
            Set.of(8),
            Set.of(),
            Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            LocalTime.of(21, 0),
            LocalTime.of(9, 0));

    BenefitCalculationResult beforeEnd =
        calculator.calculate(
            rule(Set.of(), Set.of(schedule)), context(false, "2026-08-09T08:59:59", Map.of()));
    BenefitCalculationResult atEnd =
        calculator.calculate(
            rule(Set.of(), Set.of(schedule)), context(false, "2026-08-09T09:00:00", Map.of()));

    assertTrue(beforeEnd.applicable());
    assertFalse(atEnd.applicable());
    assertEquals(BenefitRejectionReason.CONDITION_NOT_MET, atEnd.rejectionReason());
  }

  @Test
  @DisplayName("해외 거래는 다른 모든 조건이 맞아도 계산하지 않는다")
  void rejectsForeignTransactionBeforeCalculation() {
    BenefitCalculationContext foreignContext = context(true, "2026-08-08T12:00:00", Map.of());
    BenefitCalculationResult basicResult =
        calculator.calculate(rule(Set.of(), Set.of()), foreignContext);
    BenefitCalculationResult promotionResult =
        new PromotionBenefitCalculator().calculate(rule(Set.of(), Set.of()), foreignContext);

    assertFalse(basicResult.applicable());
    assertFalse(promotionResult.applicable());
    assertEquals(
        BenefitRejectionReason.FOREIGN_TRANSACTION_NOT_SUPPORTED, basicResult.rejectionReason());
    assertEquals(
        BenefitRejectionReason.FOREIGN_TRANSACTION_NOT_SUPPORTED,
        promotionResult.rejectionReason());
  }

  @Test
  @DisplayName("병원 include보다 동물병원 exclude를 우선한다")
  void veterinaryExclusionOverridesHospitalAncestor() {
    Set<BenefitRuleTarget> targets =
        Set.of(include(1, "merchant_category", "HOSPITAL"),
            exclude(1, "merchant_category", "VETERINARY"));

    BenefitCalculationResult result = calculator.calculate(
        rule(targets, Set.of()),
        context(false, "2026-08-08T12:00:00",
            Map.of("merchant_category", Set.of("HOSPITAL", "VETERINARY"))));

    assertFalse(result.applicable());
  }

  @Test
  @DisplayName("동물병원을 명시적으로 포함한 카드는 동물병원에 적용한다")
  void includesVeterinaryWhenExplicit() {
    BenefitCalculationResult result = calculator.calculate(
        rule(Set.of(include(1, "merchant_category", "VETERINARY")), Set.of()),
        context(false, "2026-08-08T12:00:00",
            Map.of("merchant_category", Set.of("HOSPITAL", "VETERINARY"))));

    assertTrue(result.applicable());
  }

  @Test
  @DisplayName("치과는 병원 상위 혜택을 상속하지만 치과 exclude가 있으면 제외한다")
  void dentalInheritsHospitalUnlessExcluded() {
    Map<String, Set<String>> dental =
        Map.of("merchant_category", Set.of("HOSPITAL", "DENTAL"));

    assertTrue(calculator.calculate(
        rule(Set.of(include(1, "merchant_category", "HOSPITAL")), Set.of()),
        context(false, "2026-08-08T12:00:00", dental)).applicable());
    assertFalse(calculator.calculate(
        rule(Set.of(include(1, "merchant_category", "HOSPITAL"),
            exclude(1, "merchant_category", "DENTAL")), Set.of()),
        context(false, "2026-08-08T12:00:00", dental)).applicable());
  }

  @Test
  @DisplayName("요양병원과 보건소 exclude는 병원 include보다 우선한다")
  void excludesNursingHospitalAndPublicHealthCenter() {
    Set<BenefitRuleTarget> targets = Set.of(
        include(1, "merchant_category", "HOSPITAL"),
        exclude(1, "merchant_category", "NURSING_HOSPITAL"),
        exclude(1, "merchant_category", "PUBLIC_HEALTH_CENTER"));

    assertFalse(calculator.calculate(rule(targets, Set.of()),
        context(false, "2026-08-08T12:00:00",
            Map.of("merchant_category", Set.of("HOSPITAL", "NURSING_HOSPITAL"))))
        .applicable());
    assertFalse(calculator.calculate(rule(targets, Set.of()),
        context(false, "2026-08-08T12:00:00",
            Map.of("merchant_category", Set.of("HOSPITAL", "PUBLIC_HEALTH_CENTER"))))
        .applicable());
  }

  @Test
  @DisplayName("PUBLIC_TRANSIT 혜택은 지하철에는 적용하고 택시에는 적용하지 않는다")
  void publicTransitDoesNotIncludeTaxi() {
    Set<BenefitRuleTarget> targets = Set.of(include(1, "merchant_category", "PUBLIC_TRANSIT"));

    assertTrue(calculator.calculate(rule(targets, Set.of()),
        context(false, "2026-08-08T12:00:00",
            Map.of("merchant_category", Set.of("TRANSPORTATION", "PUBLIC_TRANSIT", "SUBWAY"))))
        .applicable());
    assertFalse(calculator.calculate(rule(targets, Set.of()),
        context(false, "2026-08-08T12:00:00",
            Map.of("merchant_category", Set.of("TRANSPORTATION", "TAXI"))))
        .applicable());
  }

  @Test
  @DisplayName("버스·지하철·택시를 각각 명시한 혜택은 택시에도 적용한다")
  void explicitTransportAlternativesIncludeTaxi() {
    Set<BenefitRuleTarget> targets = Set.of(
        include(1, "merchant_category", "BUS"),
        include(2, "merchant_category", "SUBWAY"),
        include(3, "merchant_category", "TAXI"));

    assertTrue(calculator.calculate(rule(targets, Set.of()),
        context(false, "2026-08-08T12:00:00",
            Map.of("merchant_category", Set.of("TRANSPORTATION", "TAXI"))))
        .applicable());
  }

  @Test
  @DisplayName("스타벅스 전용 혜택은 스타벅스에는 적용하고 이디야에는 적용하지 않는다")
  void merchantExactDoesNotExpandToCategoryCompetitor() {
    Set<BenefitRuleTarget> targets = Set.of(include(1, "merchant", "STARBUCKS-ID"));

    assertTrue(calculator.calculate(rule(targets, Set.of()),
        context(false, "2026-08-08T12:00:00",
            Map.of("merchant", Set.of("STARBUCKS-ID"),
                "merchant_category", Set.of("CAFE")))).applicable());
    assertFalse(calculator.calculate(rule(targets, Set.of()),
        context(false, "2026-08-08T12:00:00",
            Map.of("merchant", Set.of("EDIYA-ID"),
                "merchant_category", Set.of("CAFE")))).applicable());
  }

  @Test
  @DisplayName("카페 category 혜택은 특정 브랜드가 아닌 일반 카페에도 적용한다")
  void categoryBenefitAppliesToGenericCafe() {
    assertTrue(calculator.calculate(
        rule(Set.of(include(1, "merchant_category", "CAFE")), Set.of()),
        context(false, "2026-08-08T12:00:00",
            Map.of("merchant_category", Set.of("CAFE")))).applicable());
  }

  @Test
  @DisplayName("전가맹점 target은 merchant master가 없어도 적용한다")
  void allMerchantsAppliesWithoutMerchantMaster() {
    assertTrue(calculator.calculate(
        rule(Set.of(include(1, "all_merchants", "ALL")), Set.of()),
        context(false, "2026-08-08T12:00:00", Map.of())).applicable());
  }

  private BenefitRule rule(Set<BenefitRuleTarget> targets, Set<BenefitRuleSchedule> schedules) {
    return new BenefitRule(
        "structured-rule",
        BenefitType.DISCOUNT,
        BenefitBasis.RATE,
        RewardUnit.KRW,
        value("0.10"),
        value("0"),
        value("0"),
        value("0"),
        value("0"),
        value("0"),
        value("0"),
        value("0"),
        BenefitPromotionCondition.NONE,
        Set.of("CAFE"),
        0,
        0,
        false,
        false,
        targets,
        schedules);
  }

  private BenefitCalculationContext context(
      boolean foreignTransaction, String approvedAt, Map<String, Set<String>> attributes) {
    return new BenefitCalculationContext(
        value("10000"),
        value("0"),
        value("0"),
        LocalDateTime.parse(approvedAt),
        "CAFE",
        false,
        0,
        0,
        true,
        true,
        foreignTransaction,
        attributes);
  }

  private BenefitRuleTarget include(int group, String targetType, String targetCode) {
    return new BenefitRuleTarget(group, BenefitTargetMatchMode.INCLUDE, targetType, targetCode);
  }

  private BenefitRuleTarget exclude(int group, String targetType, String targetCode) {
    return new BenefitRuleTarget(group, BenefitTargetMatchMode.EXCLUDE, targetType, targetCode);
  }

  private BigDecimal value(String value) {
    return new BigDecimal(value);
  }
}
