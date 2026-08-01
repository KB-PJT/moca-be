package com.moca.mocabe.domain.codef.controller;

import com.moca.mocabe.domain.codef.dto.CardLinkResponse;
import com.moca.mocabe.domain.codef.dto.CreateCardLinkRequest;
import com.moca.mocabe.domain.codef.service.CardLinkService;
import com.moca.mocabe.global.auth.CurrentUserProvider;
import com.moca.mocabe.global.response.ApiResponse;

import javax.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
