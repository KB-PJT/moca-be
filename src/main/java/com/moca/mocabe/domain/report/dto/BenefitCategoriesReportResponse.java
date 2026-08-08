package com.moca.mocabe.domain.report.dto;

import java.util.List;

/** 월별 상위 혜택 카테고리 리포트다. */
public record BenefitCategoriesReportResponse(
    String yearMonth, List<BenefitCategoryResponse> categories) { }
