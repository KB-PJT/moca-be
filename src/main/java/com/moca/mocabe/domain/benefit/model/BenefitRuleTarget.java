package com.moca.mocabe.domain.benefit.model;

import java.util.Locale;

import com.moca.mocabe.domain.benefit.type.BenefitTargetMatchMode;

/**
 * 혜택 규칙의 정규화된 적용·제외 대상 한 건이다.
 */
public record BenefitRuleTarget(
        int conditionGroup,
        BenefitTargetMatchMode matchMode,
        String targetType,
        String targetCode
) {

    public BenefitRuleTarget {
        if (conditionGroup <= 0) {
            throw new IllegalArgumentException("conditionGroup은 1 이상이어야 합니다.");
        }
        if (matchMode == null) {
            throw new IllegalArgumentException("matchMode는 필수입니다.");
        }
        targetType = normalizeRequired(targetType, "targetType");
        targetCode = normalizeRequired(targetCode, "targetCode");
    }

    private static String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "은 비어 있을 수 없습니다.");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
