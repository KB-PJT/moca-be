package com.moca.mocabe.domain.support.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moca.mocabe.domain.support.dto.InquiryResponse;
import com.moca.mocabe.domain.support.mapper.SupportInquiryMapper;
import com.moca.mocabe.domain.support.model.InquiryRow;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SupportInquiryServiceTest {

    private static final String USER_ID = "01980d6a-5c0c-7aaf-9b85-010203040506";

    @Mock
    private SupportInquiryMapper supportInquiryMapper;

    private SupportInquiryService supportInquiryService;

    @BeforeEach
    void setUp() {
        supportInquiryService = new SupportInquiryService(supportInquiryMapper);
    }

    @Test
    @DisplayName("새 문의 식별자를 생성해 저장하고, 저장된 문의를 조회해 응답으로 반환한다")
    void createsInquiryAndReturnsSavedRow() {
        InquiryRow savedRow = new InquiryRow();
        savedRow.setInquiryType("card_link");
        savedRow.setTitle("카드 연동이 안 돼요");
        savedRow.setContent("계속 실패합니다.");
        savedRow.setReplyEmail("kakao_jimin@kakao.com");
        savedRow.setStatus("received");
        savedRow.setCreatedAt(LocalDateTime.of(2026, 8, 10, 10, 30));
        ArgumentCaptor<String> inquiryIdCaptor = ArgumentCaptor.forClass(String.class);
        when(supportInquiryMapper.findByInquiryId(any(), eq(USER_ID))).thenAnswer(invocation -> {
            savedRow.setInquiryId(invocation.getArgument(0));
            return savedRow;
        });

        InquiryResponse response = supportInquiryService.createInquiry(
                USER_ID, "card_link", "카드 연동이 안 돼요", "계속 실패합니다.", "kakao_jimin@kakao.com");

        verify(supportInquiryMapper).insertInquiry(inquiryIdCaptor.capture(), eq(USER_ID), eq("card_link"),
                eq("카드 연동이 안 돼요"), eq("계속 실패합니다."), eq("kakao_jimin@kakao.com"));
        assertEquals(inquiryIdCaptor.getValue(), response.getInquiryId());
        assertEquals("card_link", response.getInquiryType());
        assertEquals("카드 연동이 안 돼요", response.getTitle());
        assertEquals("계속 실패합니다.", response.getContent());
        assertEquals("kakao_jimin@kakao.com", response.getReplyEmail());
        assertEquals("received", response.getStatus());
    }

    @Test
    @DisplayName("회원 탈퇴 시 해당 사용자의 문의 삭제를 매퍼에 위임한다")
    void deletesAllByUserId() {
        supportInquiryService.deleteAllByUserId(USER_ID);

        verify(supportInquiryMapper).deleteByUserId(USER_ID);
    }
}
