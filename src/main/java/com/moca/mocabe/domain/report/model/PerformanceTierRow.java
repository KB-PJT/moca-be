package com.moca.mocabe.domain.report.model;

/** 보유 카드별 콘텐츠 실적 구간 조회 행이다. */
public record PerformanceTierRow(String userCardId, int tier, long targetAmount) { }
