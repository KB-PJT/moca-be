package com.moca.mocabe.domain.benefit.calculation;

import com.moca.mocabe.domain.benefit.model.BenefitCalculationContext;
import com.moca.mocabe.domain.benefit.model.BenefitRuleTarget;
import com.moca.mocabe.domain.benefit.type.BenefitTargetMatchMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 같은 condition_group의 include는 AND, 서로 다른 그룹은 OR로 평가한다. exclude는 그룹과 무관하게 하나라도 일치하면 최종 대상에서 제외한다.
 */
public class BenefitRuleTargetEvaluator {

  public boolean matches(Set<BenefitRuleTarget> targets, BenefitCalculationContext context) {
    if (targets == null || targets.isEmpty()) {
      return true;
    }

    boolean excluded =
        targets.stream()
            .filter(target -> target.matchMode() == BenefitTargetMatchMode.EXCLUDE)
            .anyMatch(target -> matches(target, context));
    if (excluded) {
      return false;
    }

    Map<Integer, List<BenefitRuleTarget>> includeGroups =
        targets.stream()
            .filter(target -> target.matchMode() == BenefitTargetMatchMode.INCLUDE)
            .collect(Collectors.groupingBy(BenefitRuleTarget::conditionGroup));
    return includeGroups.isEmpty()
        || includeGroups.values().stream()
            .anyMatch(group -> group.stream().allMatch(target -> matches(target, context)));
  }

  private boolean matches(BenefitRuleTarget target, BenefitCalculationContext context) {
    return context.hasTarget(target.targetType(), target.targetCode());
  }
}
