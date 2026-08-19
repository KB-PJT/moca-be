package com.moca.mocabe.domain.benefit.model;

/** 공통 parser에 전달할 미구조화 카드 혜택 원문과 기존 첫 offer다. */
public record RawBenefitStructuringCandidate(
    String benefitId, String offerId, String title, String summary, String detailText) { }
