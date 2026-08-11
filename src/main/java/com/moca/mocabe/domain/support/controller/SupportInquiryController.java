package com.moca.mocabe.domain.support.controller;

import com.moca.mocabe.domain.support.dto.CreateInquiryRequest;
import com.moca.mocabe.domain.support.dto.InquiryResponse;
import com.moca.mocabe.domain.support.service.SupportInquiryService;
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

/** 인증 사용자의 문의 등록 API를 제공한다. */
@RestController
@RequestMapping("/support/inquiries")
@RequiredArgsConstructor
public class SupportInquiryController {

    private final SupportInquiryService supportInquiryService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    public ResponseEntity<ApiResponse<InquiryResponse>> createInquiry(
            @Valid @RequestBody CreateInquiryRequest request) {
        InquiryResponse response = supportInquiryService.createInquiry(
                currentUserProvider.getCurrentUserId(), request.getInquiryType(), request.getTitle(),
                request.getContent(), request.getReplyEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}
