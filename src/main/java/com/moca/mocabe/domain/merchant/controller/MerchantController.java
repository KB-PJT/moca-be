package com.moca.mocabe.domain.merchant.controller;

import com.moca.mocabe.domain.merchant.dto.MerchantCategoryResponse;
import com.moca.mocabe.domain.merchant.service.MerchantCategoryQueryService;
import com.moca.mocabe.global.response.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 가맹점 카테고리 조회 API를 제공한다. */
@RestController
@RequestMapping("/merchants")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantCategoryQueryService merchantCategoryQueryService;

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<MerchantCategoryResponse>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.success(merchantCategoryQueryService.getCategories()));
    }
}
