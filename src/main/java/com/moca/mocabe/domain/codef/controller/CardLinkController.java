package com.moca.mocabe.domain.codef.controller;

import com.moca.mocabe.domain.codef.dto.ActivateCardLinkCardsRequest;
import com.moca.mocabe.domain.codef.dto.ActivateCardLinkCardsResponse;
import com.moca.mocabe.domain.codef.dto.CardLinkCardResponse;
import com.moca.mocabe.domain.codef.dto.CardLinkResponse;
import com.moca.mocabe.domain.codef.dto.CreateCardLinkRequest;
import com.moca.mocabe.domain.codef.dto.SubmitCardCredentialsRequest;
import com.moca.mocabe.domain.codef.dto.SyncOwnedCardsResponse;
import com.moca.mocabe.domain.codef.service.CardLinkService;
import com.moca.mocabe.global.auth.CurrentUserProvider;
import com.moca.mocabe.global.response.ApiResponse;

import javax.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** CODEF 카드 연동(Connected ID 생성) API를 제공한다. */
@RestController
@RequestMapping("/card-links")
@RequiredArgsConstructor
public class CardLinkController {

    private final CardLinkService cardLinkService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    public ResponseEntity<ApiResponse<CardLinkResponse>> createLink(
            @Valid @RequestBody CreateCardLinkRequest request) {
        CardLinkResponse response = cardLinkService.createLink(
                currentUserProvider.getCurrentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/{linkId}/cards/discover")
    public ApiResponse<CardLinkResponse> discoverOwnedCards(@PathVariable String linkId) {
        return ApiResponse.success(cardLinkService.discoverOwnedCards(
                currentUserProvider.getCurrentUserId(), linkId));
    }

    @PatchMapping("/{linkId}/cards")
    public ApiResponse<ActivateCardLinkCardsResponse> activateCards(
            @PathVariable String linkId,
            @Valid @RequestBody ActivateCardLinkCardsRequest request) {
        return ApiResponse.success(cardLinkService.activateCards(
                currentUserProvider.getCurrentUserId(), linkId, request));
    }

    @PostMapping("/cards/sync")
    public ApiResponse<SyncOwnedCardsResponse> syncOwnedCards(
            @RequestParam(name = "institutionCode", required = false) String institutionCode) {
        return ApiResponse.success(cardLinkService.syncOwnedCards(
                currentUserProvider.getCurrentUserId(), institutionCode));
    }

    @PatchMapping("/cards/{userCardId}/credentials")
    public ApiResponse<CardLinkCardResponse> submitCardCredentials(
            @PathVariable String userCardId,
            @Valid @RequestBody SubmitCardCredentialsRequest request) {
        return ApiResponse.success(cardLinkService.submitCardCredentials(
                currentUserProvider.getCurrentUserId(), userCardId, request));
    }
}
