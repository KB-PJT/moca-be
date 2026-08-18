package com.moca.mocabe.domain.benefit.structuring;

import java.math.BigDecimal;

/** 전월 실적 구간의 하한·상한을 분리해 상위 구간과 중복 적용되지 않게 한다. */
public record ParsedPerformanceTier(BigDecimal minimumKrw, BigDecimal maximumExclusiveKrw) { }
