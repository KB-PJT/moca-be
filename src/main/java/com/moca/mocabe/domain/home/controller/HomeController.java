package com.moca.mocabe.domain.home.controller;

import com.moca.mocabe.domain.home.dto.HomeCardsResponse;
import com.moca.mocabe.domain.home.dto.HomeGreetingResponse;
import com.moca.mocabe.domain.home.dto.RecentHistoryResponse;
import com.moca.mocabe.domain.home.service.HomeQueryService;
import com.moca.mocabe.global.auth.CurrentUserProvider;
import com.moca.mocabe.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 홈 화면 컴포넌트별 조회 API를 제공한다. */
@RestController
@RequestMapping("/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeQueryService homeQueryService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/greeting")
    public ResponseEntity<ApiResponse<HomeGreetingResponse>> getGreeting(
            @RequestParam(name = "yearMonth", required = false) String yearMonth) {
        String userId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(homeQueryService.getGreeting(userId, yearMonth)));
    }

    @GetMapping("/cards")
    public ResponseEntity<ApiResponse<HomeCardsResponse>> getCards(
            @RequestParam(name = "yearMonth", required = false) String yearMonth,
            @RequestParam(name = "orderMode", required = false) String orderMode) {
        String userId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(homeQueryService.getCards(userId, yearMonth, orderMode)));
    }

    @GetMapping("/recent-history")
    public ResponseEntity<ApiResponse<RecentHistoryResponse>> getRecentHistory(
            @RequestParam(name = "yearMonth", required = false) String yearMonth,
            @RequestParam(name = "limit", defaultValue = "5") int limit) {
        String userId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                homeQueryService.getRecentHistory(userId, yearMonth, limit)));
    }
}
