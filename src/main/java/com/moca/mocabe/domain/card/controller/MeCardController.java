package com.moca.mocabe.domain.card.controller;

import com.moca.mocabe.domain.card.dto.MeCardItemResponse;
import com.moca.mocabe.domain.card.dto.CardDetailResponse;
import com.moca.mocabe.domain.card.dto.MeCardsResponse;
import com.moca.mocabe.domain.card.dto.SyncMyCardsResponse;
import com.moca.mocabe.domain.card.dto.UpdateMemoRequest;
import com.moca.mocabe.domain.card.service.CardQueryService;
import com.moca.mocabe.domain.codef.service.CardSyncService;
import com.moca.mocabe.domain.user.dto.SuccessResponse;
import com.moca.mocabe.global.auth.CurrentUserProvider;
import com.moca.mocabe.global.response.ApiResponse;
import java.time.LocalDate;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 인증 사용자의 보유 카드 목록·승인내역 동기화 API를 제공한다. */
@RestController
@RequestMapping("/me/cards")
@RequiredArgsConstructor
public class MeCardController {

    private final CardQueryService cardQueryService;
    private final CardSyncService cardSyncService;
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

    @GetMapping("/{userCardId}")
    public ResponseEntity<ApiResponse<CardDetailResponse>> getCardDetail(
            @PathVariable String userCardId
    ) {
        CardDetailResponse response = cardQueryService.getCardDetail(
                currentUserProvider.getCurrentUserId(), userCardId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{userCardId}/memo")
    public ResponseEntity<ApiResponse<MeCardItemResponse>> updateMemo(
            @PathVariable String userCardId,
            @Valid @RequestBody UpdateMemoRequest request
    ) {
        MeCardItemResponse response = cardQueryService.updateMemo(
                currentUserProvider.getCurrentUserId(), userCardId, request.getMemo());
        return ResponseEntity.ok(ApiResponse.success(response));
    }


    @DeleteMapping("/{userCardId}")
    public ResponseEntity<ApiResponse<SuccessResponse>> disconnectCard(
            @PathVariable String userCardId
    ) {
        cardQueryService.disconnectCard(currentUserProvider.getCurrentUserId(), userCardId);
        return ResponseEntity.ok(ApiResponse.success(new SuccessResponse(true)));
    }

    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<SyncMyCardsResponse>> sync(
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        // startDate/endDate를 생략하면 이번 달 1일~오늘 범위로 승인내역을 조회·적재한다.
        SyncMyCardsResponse response = cardSyncService.sync(
                currentUserProvider.getCurrentUserId(), startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
