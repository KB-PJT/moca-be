package com.moca.mocabe.domain.support.service;

import com.moca.mocabe.domain.support.dto.InquiryResponse;
import com.moca.mocabe.domain.support.mapper.SupportInquiryMapper;
import com.moca.mocabe.domain.support.model.InquiryRow;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** 문의 등록에 필요한 도메인 규칙과 영속성 접근을 담당한다. */
public class SupportInquiryService {

    private final SupportInquiryMapper supportInquiryMapper;

    public SupportInquiryService(SupportInquiryMapper supportInquiryMapper) {
        this.supportInquiryMapper = supportInquiryMapper;
    }

    @Transactional
    public InquiryResponse createInquiry(
            String userId, String inquiryType, String title, String content, String replyEmail) {
        String inquiryId = UUID.randomUUID().toString();
        supportInquiryMapper.insertInquiry(inquiryId, userId, inquiryType, title, content, replyEmail);
        InquiryRow row = supportInquiryMapper.findByInquiryId(inquiryId, userId);
        return new InquiryResponse(row);
    }

    /** 회원 탈퇴 시 해당 사용자의 문의 내역을 정리한다. */
    @Transactional
    public void deleteAllByUserId(String userId) {
        supportInquiryMapper.deleteByUserId(userId);
    }
}
