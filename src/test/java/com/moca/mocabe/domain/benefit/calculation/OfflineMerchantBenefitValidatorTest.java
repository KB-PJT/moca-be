package com.moca.mocabe.domain.benefit.calculation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moca.mocabe.domain.benefit.model.BenefitCalculationContext;
import com.moca.mocabe.domain.benefit.model.BenefitRuleTarget;
import com.moca.mocabe.domain.benefit.type.BenefitTargetMatchMode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("오프라인 가맹점 혜택 Validator")
class OfflineMerchantBenefitValidatorTest {
  private final OfflineMerchantBenefitValidator validator = new OfflineMerchantBenefitValidator();
  private final BenefitRuleTargetEvaluator targetEvaluator = new BenefitRuleTargetEvaluator();

  @Test
  @DisplayName("백화점에 입점한 편의점은 혜택 대상에서 제외한다")
  void rejectsConvenienceStoreInsideDepartmentStore() {
    assertFalse(validator.isEligible(targets(), context("DEPARTMENT_STORE")));
    assertFalse(targetEvaluator.matches(targets(), context("DEPARTMENT_STORE")));
  }

  @Test
  @DisplayName("일반 편의점은 혜택 대상에 포함한다")
  void acceptsStandaloneConvenienceStore() {
    assertTrue(validator.isEligible(targets(), context("CONVENIENCE_STORE")));
  }

  @Test
  @DisplayName("공백과 소문자가 섞인 INCLUDE 가맹점 코드도 편의점으로 판정한다")
  void normalizesIncludedMerchantCode() {
    Set<BenefitRuleTarget> normalizedTarget = Set.of(
        new BenefitRuleTarget(1, BenefitTargetMatchMode.INCLUDE, " merchant ", " cu "));
    assertFalse(validator.isEligible(normalizedTarget, context("LARGE_MART")));
  }

  @Test
  @DisplayName("EXCLUDE 편의점 대상은 호스트 업종 검증을 발동하지 않는다")
  void ignoresExcludedConvenienceMerchantTarget() {
    Set<BenefitRuleTarget> excludedTarget = Set.of(
        new BenefitRuleTarget(1, BenefitTargetMatchMode.EXCLUDE, "merchant", "CU"));
    assertTrue(validator.isEligible(excludedTarget, context("DEPARTMENT_STORE")));
  }

  @Test
  @DisplayName("일치하지 않는 제외 대상만 있으면 혜택을 적용한다")
  void acceptsWhenExcludeTargetDoesNotMatch() {
    Set<BenefitRuleTarget> exclusions = Set.of(
        new BenefitRuleTarget(1, BenefitTargetMatchMode.EXCLUDE, "merchant", "GS25"));
    assertTrue(targetEvaluator.matches(exclusions, context("CONVENIENCE_STORE")));
  }

  private Set<BenefitRuleTarget> targets() {
    return Set.of(new BenefitRuleTarget(1, BenefitTargetMatchMode.INCLUDE, "merchant", "CU"));
  }

  private BenefitCalculationContext context(String category) {
    return new BenefitCalculationContext(
        new BigDecimal("10000"),
        BigDecimal.ONE,
        new BigDecimal("200000"),
        LocalDateTime.of(2026, 8, 21, 12, 0),
        "CONVENIENCE_STORE",
        false,
        0,
        0,
        true,
        true,
        false,
        Map.of("MERCHANT_CATEGORY_CODE", Set.of(category)));
  }
}
