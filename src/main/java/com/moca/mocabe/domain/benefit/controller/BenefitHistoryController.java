package com.moca.mocabe.domain.benefit.controller;

import com.moca.mocabe.domain.benefit.dto.BenefitHistoryDetailResponse;
import com.moca.mocabe.domain.benefit.dto.BenefitHistoryResponse;
import com.moca.mocabe.domain.benefit.service.BenefitHistoryQueryService;
import com.moca.mocabe.global.auth.CurrentUserProvider;
import com.moca.mocabe.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 인증 사용자의 전체 카드 결제 내역 목록과 상세를 제공한다. */
@RestController
@RequestMapping("/benefit-history")
@RequiredArgsConstructor
public class BenefitHistoryController {
  private final BenefitHistoryQueryService benefitHistoryQueryService;
  private final CurrentUserProvider currentUserProvider;

  @GetMapping
  public ResponseEntity<ApiResponse<BenefitHistoryResponse>> getHistory(
      @RequestParam(required = false) String yearMonth,
      @RequestParam(required = false) String userCardId,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return ResponseEntity.ok(
        ApiResponse.success(
            benefitHistoryQueryService.getHistory(
                currentUserProvider.getCurrentUserId(),
                yearMonth,
                userCardId,
                type,
                sort,
                page,
                size)));
  }

  @GetMapping("/{benefitHistoryId}")
  public ResponseEntity<ApiResponse<BenefitHistoryDetailResponse>> getDetail(
      @PathVariable String benefitHistoryId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            benefitHistoryQueryService.getDetail(
                currentUserProvider.getCurrentUserId(), benefitHistoryId)));
  }
}
