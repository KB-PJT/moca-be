package com.moca.mocabe.domain.benefit.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

@DisplayName("구조화 카드 혜택 적용 조건")
class StructuredBenefitEligibilityTest {

    private final BenefitCalculator calculator = new BasicBenefitCalculator();

    @Test
    @DisplayName("컴포즈 오프라인 또는 공식 앱 결제 조건을 그룹 OR로 평가한다")
    void matchesAnyIncludeConditionGroup() {
        Set<BenefitRuleTarget> targets = Set.of(
                include(1, "merchant", "COMPOSE"),
                include(1, "channel", "OFFLINE"),
                include(2, "merchant", "COMPOSE"),
                include(2, "entry_method", "OFFICIAL_APP"));

        BenefitCalculationResult result = calculator.calculate(rule(targets, Set.of()), context(
                false, "2026-08-08T12:00:00",
                Map.of("merchant", Set.of("COMPOSE"), "entry_method", Set.of("OFFICIAL_APP"))));

        assertTrue(result.applicable());
    }

    @Test
    @DisplayName("include 그룹 일부만 맞으면 대상이 아니다")
    void requiresEveryIncludeInMatchedGroup() {
        Set<BenefitRuleTarget> targets = Set.of(
                include(1, "merchant", "COMPOSE"),
                include(1, "channel", "OFFLINE"));

        BenefitCalculationResult result = calculator.calculate(rule(targets, Set.of()), context(
                false, "2026-08-08T12:00:00", Map.of("merchant", Set.of("COMPOSE"))));

        assertFalse(result.applicable());
        assertEquals(BenefitRejectionReason.TARGET_NOT_MATCHED, result.rejectionReason());
    }

    @Test
    @DisplayName("상품권 exclude는 include가 맞아도 최종 적용에서 제외한다")
    void exclusionOverridesIncludedGroup() {
        Set<BenefitRuleTarget> targets = Set.of(
                include(1, "merchant_category", "CAFE"),
                exclude(1, "product", "GIFT_CARD"));

        BenefitCalculationResult result = calculator.calculate(rule(targets, Set.of()), context(
                false, "2026-08-08T12:00:00", Map.of("product", Set.of("GIFT_CARD"))));

        assertFalse(result.applicable());
        assertEquals(BenefitRejectionReason.TARGET_NOT_MATCHED, result.rejectionReason());
    }

    @Test
    @DisplayName("21시부터 다음 날 9시 전까지인 자정 통과 일정을 적용한다")
    void matchesCrossMidnightSchedule() {
        BenefitRuleSchedule schedule = new BenefitRuleSchedule(
                Set.of(8), Set.of(), Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
                LocalTime.of(21, 0), LocalTime.of(9, 0));

        BenefitCalculationResult beforeEnd = calculator.calculate(rule(Set.of(), Set.of(schedule)), context(
                false, "2026-08-09T08:59:59", Map.of()));
        BenefitCalculationResult atEnd = calculator.calculate(rule(Set.of(), Set.of(schedule)), context(
                false, "2026-08-09T09:00:00", Map.of()));

        assertTrue(beforeEnd.applicable());
        assertFalse(atEnd.applicable());
        assertEquals(BenefitRejectionReason.CONDITION_NOT_MET, atEnd.rejectionReason());
    }

    @Test
    @DisplayName("해외 거래는 다른 모든 조건이 맞아도 계산하지 않는다")
    void rejectsForeignTransactionBeforeCalculation() {
        BenefitCalculationContext foreignContext = context(true, "2026-08-08T12:00:00", Map.of());
        BenefitCalculationResult basicResult = calculator.calculate(rule(Set.of(), Set.of()), foreignContext);
        BenefitCalculationResult promotionResult = new PromotionBenefitCalculator().calculate(
                rule(Set.of(), Set.of()),
                foreignContext);

        assertFalse(basicResult.applicable());
        assertFalse(promotionResult.applicable());
        assertEquals(BenefitRejectionReason.FOREIGN_TRANSACTION_NOT_SUPPORTED, basicResult.rejectionReason());
        assertEquals(BenefitRejectionReason.FOREIGN_TRANSACTION_NOT_SUPPORTED, promotionResult.rejectionReason());
    }

    private BenefitRule rule(Set<BenefitRuleTarget> targets, Set<BenefitRuleSchedule> schedules) {
        return new BenefitRule(
                "structured-rule", BenefitType.DISCOUNT, BenefitBasis.RATE, RewardUnit.KRW,
                value("0.10"), value("0"), value("0"), value("0"), value("0"), value("0"),
                value("0"), value("0"), BenefitPromotionCondition.NONE, Set.of("CAFE"),
                0, 0, false, false, targets, schedules);
    }

    private BenefitCalculationContext context(boolean foreignTransaction, String approvedAt,
            Map<String, Set<String>> attributes) {
        return new BenefitCalculationContext(
                value("10000"), value("0"), value("0"), LocalDateTime.parse(approvedAt), "CAFE",
                false, 0, 0, true, true, foreignTransaction, attributes);
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
