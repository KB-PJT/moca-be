package com.moca.mocabe.domain.report.controller;

import com.moca.mocabe.domain.report.dto.BenefitCategoriesReportResponse;
import com.moca.mocabe.domain.report.dto.BenefitSummaryReportResponse;
import com.moca.mocabe.domain.report.dto.MissedBenefitsReportResponse;
import com.moca.mocabe.domain.report.dto.PerformanceCardsReportResponse;
import com.moca.mocabe.domain.report.dto.PerformanceSummaryReportResponse;
import com.moca.mocabe.domain.report.service.ReportQueryService;
import com.moca.mocabe.global.auth.CurrentUserProvider;
import com.moca.mocabe.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 혜택과 카드 실적 리포트 API를 제공한다. */
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

  private final ReportQueryService reportQueryService;
  private final CurrentUserProvider currentUserProvider;

  @GetMapping("/benefits/summary")
  public ResponseEntity<ApiResponse<BenefitSummaryReportResponse>> getBenefitSummary(
      @RequestParam(name = "yearMonth", required = false) String yearMonth) {
    return ResponseEntity.ok(
        ApiResponse.success(
            reportQueryService.getBenefitSummary(
                currentUserProvider.getCurrentUserId(), yearMonth)));
  }

  @GetMapping("/benefits/categories")
  public ResponseEntity<ApiResponse<BenefitCategoriesReportResponse>> getBenefitCategories(
      @RequestParam(name = "yearMonth", required = false) String yearMonth,
      @RequestParam(name = "limit", defaultValue = "3") int limit) {
    return ResponseEntity.ok(
        ApiResponse.success(
            reportQueryService.getBenefitCategories(
                currentUserProvider.getCurrentUserId(), yearMonth, limit)));
  }

  @GetMapping("/benefits/missed")
  public ResponseEntity<ApiResponse<MissedBenefitsReportResponse>> getMissedBenefits(
      @RequestParam(name = "yearMonth", required = false) String yearMonth,
      @RequestParam(name = "userCardId") String userCardId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            reportQueryService.getMissedBenefits(
                currentUserProvider.getCurrentUserId(), yearMonth, userCardId)));
  }

  @GetMapping("/performances/summary")
  public ResponseEntity<ApiResponse<PerformanceSummaryReportResponse>> getPerformanceSummary(
      @RequestParam(name = "yearMonth", required = false) String yearMonth) {
    return ResponseEntity.ok(
        ApiResponse.success(
            reportQueryService.getPerformanceSummary(
                currentUserProvider.getCurrentUserId(), yearMonth)));
  }

  @GetMapping("/performances/cards")
  public ResponseEntity<ApiResponse<PerformanceCardsReportResponse>> getPerformanceCards(
      @RequestParam(name = "yearMonth", required = false) String yearMonth) {
    return ResponseEntity.ok(
        ApiResponse.success(
            reportQueryService.getPerformanceCards(
                currentUserProvider.getCurrentUserId(), yearMonth)));
  }
}
