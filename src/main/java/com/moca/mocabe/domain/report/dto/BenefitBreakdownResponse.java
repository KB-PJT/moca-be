package com.moca.mocabe.domain.report.dto;

/** 월별 혜택 보상 유형 집계 응답이다. */
public record BenefitBreakdownResponse(String type, String label, long amount) { }
