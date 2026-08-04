package com.moca.mocabe.domain.home.dto;

import java.util.List;

/** 홈 최근 혜택 내역 목록이다. */
public class RecentBenefitsResponse {

    private final List<RecentBenefitItemResponse> benefits;

    public RecentBenefitsResponse(List<RecentBenefitItemResponse> benefits) {
        this.benefits = benefits;
    }

    public List<RecentBenefitItemResponse> getBenefits() {
        return benefits;
    }
}
