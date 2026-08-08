package com.moca.mocabe.domain.report.model;

/** 가맹점 카테고리별 혜택 집계 행이다. */
public record CategoryBenefitRow(String categoryCode, String categoryName, long benefitAmount) { }
