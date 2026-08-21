package com.moca.mocabe.domain.benefit.model;

import java.math.BigDecimal;

/** 사용자 카드의 특정 월·혜택 영역별 인정 사용액이다. */
public record BenefitAreaSpendRow(
    String areaGroupKey,
    String areaKey,
    String areaName,
    int displayOrder,
    BigDecimal eligibleAmountKrw,
    int transactionCount) { }
