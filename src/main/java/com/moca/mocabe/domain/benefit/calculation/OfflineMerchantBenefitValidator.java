package com.moca.mocabe.domain.benefit.calculation;

import com.moca.mocabe.domain.benefit.model.BenefitCalculationContext;
import com.moca.mocabe.domain.benefit.model.BenefitRuleTarget;
import com.moca.mocabe.domain.benefit.type.BenefitTargetMatchMode;
import java.util.Locale;
import java.util.Set;

/** 오프라인 편의점 혜택의 입점 매장 오인 적용을 막는 검증기다. */
public class OfflineMerchantBenefitValidator {
  private static final Set<String> CONVENIENCE_MERCHANTS =
      Set.of("CU", "GS25", "세븐일레븐", "이마트24");
  private static final Set<String> CAFE_MERCHANTS =
      Set.of("스타벅스", "투썸플레이스", "커피빈", "폴바셋", "이디야", "메가MGC커피",
          "컴포즈커피", "매머드", "매머드커피", "빽다방");
  private static final Set<String> EXCLUDED_HOST_CATEGORIES =
      Set.of("DEPARTMENT_STORE", "LARGE_MART", "SHOPPING_MALL", "DUTY_FREE", "AIRPORT");

  public boolean isEligible(Set<BenefitRuleTarget> targets, BenefitCalculationContext context) {
    boolean hostedMerchantTarget = targets.stream()
        .anyMatch(target -> target.matchMode() == BenefitTargetMatchMode.INCLUDE
            && "MERCHANT".equals(normalize(target.targetType()))
            && (CONVENIENCE_MERCHANTS.contains(normalize(target.targetCode()))
                || CAFE_MERCHANTS.contains(normalize(target.targetCode()))));
    if (!hostedMerchantTarget) {
      return true;
    }
    return EXCLUDED_HOST_CATEGORIES.stream()
        .noneMatch(category -> context.hasTarget("MERCHANT_CATEGORY_CODE", category));
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }
}
