package com.moca.mocabe.domain.benefit.dto;

/** 전월 실적 미충족으로 혜택을 받지 못한 경우의 실적 현황이다. */
public record PerformanceShortfallResponse(
    long requiredAmount, long achievedAmount, long remainingAmount) { }
