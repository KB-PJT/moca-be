package com.moca.mocabe.domain.benefit.calculation;

import com.moca.mocabe.domain.benefit.model.BenefitCalculationContext;
import com.moca.mocabe.domain.benefit.model.BenefitCalculationResult;
import com.moca.mocabe.domain.benefit.model.BenefitRule;

/**
 * 카드 혜택 룰과 결제 상황을 비교해 예상 혜택을 계산하는 계약이다.
 */
public interface BenefitCalculator {

    BenefitCalculationResult calculate(BenefitRule rule, BenefitCalculationContext context);
}
