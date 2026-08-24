package com.moca.mocabe.domain.benefit.controller;

import com.moca.mocabe.domain.benefit.dto.BenefitRecalculationResponse;
import com.moca.mocabe.domain.benefit.service.BenefitUsageCalculationService;
import com.moca.mocabe.global.auth.CurrentUserProvider;
import com.moca.mocabe.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 인증 사용자의 혜택 계산 결과를 지정 월 기준으로 재생성한다. */
@RestController
@RequestMapping("/benefits")
@RequiredArgsConstructor
public class BenefitRecalculationController {
  private final BenefitUsageCalculationService benefitUsageCalculationService;
  private final CurrentUserProvider currentUserProvider;

  @PostMapping("/recalculate")
  public ResponseEntity<ApiResponse<BenefitRecalculationResponse>> recalculate(
      @RequestParam(required = false) String yearMonth) {
    String recalculatedMonth =
        benefitUsageCalculationService.recalculateForMonth(
            currentUserProvider.getCurrentUserId(), yearMonth);
    return ResponseEntity.ok(
        ApiResponse.success(new BenefitRecalculationResponse(recalculatedMonth)));
  }
}
