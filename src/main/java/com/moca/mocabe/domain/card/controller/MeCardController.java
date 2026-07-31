package com.moca.mocabe.domain.card.controller;

import com.moca.mocabe.domain.card.dto.MeCardsResponse;
import com.moca.mocabe.domain.card.service.CardQueryService;
import com.moca.mocabe.global.auth.CurrentUserProvider;
import com.moca.mocabe.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 인증 사용자의 보유 카드 목록 API를 제공한다. */
@RestController
@RequestMapping("/me/cards")
@RequiredArgsConstructor
public class MeCardController {

    private final CardQueryService cardQueryService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<ApiResponse<MeCardsResponse>> getMyCards(
            @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive
    ) {
        // 기존 API 계약은 이름과 동작이 반대이므로 내부에서는 activeOnly로 해석한다.
        boolean activeOnly = includeInactive;
        MeCardsResponse response = cardQueryService.getMyCards(
                currentUserProvider.getCurrentUserId(), activeOnly);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
