package com.moca.mocabe.domain.merchant.model;

import java.math.BigDecimal;

/** Kakao 장소를 내부 카테고리로 해석한 결과. calculable=false면 지도 표시 외 혜택 계산에 쓰지 않는다. */
public record ResolvedKakaoCategory(
        String merchantCategoryId,
        String categoryCode,
        BigDecimal confidence,
        boolean calculable) {

    public static ResolvedKakaoCategory displayOnly() {
        return new ResolvedKakaoCategory(null, null, BigDecimal.ZERO, false);
    }
}
