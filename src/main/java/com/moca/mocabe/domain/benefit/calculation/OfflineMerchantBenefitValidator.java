package com.moca.mocabe.domain.benefit.calculation;

import com.moca.mocabe.domain.benefit.model.BenefitCalculationContext;
import com.moca.mocabe.domain.benefit.model.BenefitRuleTarget;
import java.util.Locale;
import java.util.Set;

/** 오프라인 편의점 혜택의 입점 매장 오인 적용을 막는 검증기다. */
public class OfflineMerchantBenefitValidator {
  private static final Set<String> CONVENIENCE_MERCHANTS = Set.of("CU", "GS25", "세븐일레븐");
  private static final Set<String> EXCLUDED_HOST_CATEGORIES =
      Set.of("DEPARTMENT_STORE", "LARGE_MART", "DUTY_FREE", "AIRPORT");

  public boolean isEligible(Set<BenefitRuleTarget> targets, BenefitCalculationContext context) {
    boolean convenienceTarget = targets.stream()
        .anyMatch(target -> "MERCHANT".equals(normalize(target.targetType()))
            && CONVENIENCE_MERCHANTS.contains(target.targetCode()));
    if (!convenienceTarget) {
      return true;
    }
    return EXCLUDED_HOST_CATEGORIES.stream()
        .noneMatch(category -> context.hasTarget("MERCHANT_CATEGORY_CODE", category));
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }
}
