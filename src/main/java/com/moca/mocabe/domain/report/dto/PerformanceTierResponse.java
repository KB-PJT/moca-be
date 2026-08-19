package com.moca.mocabe.domain.report.dto;

/** 카드 콘텐츠에 정의된 월 실적 구간이다. */
public record PerformanceTierResponse(int tier, long targetAmount) { }
