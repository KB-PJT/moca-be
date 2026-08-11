package com.moca.mocabe.domain.support.dto;

import com.moca.mocabe.domain.support.model.InquiryRow;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 문의 등록 결과 응답이다. */
public final class InquiryResponse {

    private final String inquiryId;
    private final String inquiryType;
    private final String title;
    private final String content;
    private final String replyEmail;
    private final String status;
    private final String createdAt;

    public InquiryResponse(InquiryRow row) {
        this.inquiryId = row.getInquiryId();
        this.inquiryType = row.getInquiryType();
        this.title = row.getTitle();
        this.content = row.getContent();
        this.replyEmail = row.getReplyEmail();
        this.status = row.getStatus();
        this.createdAt = format(row.getCreatedAt());
    }

    private static String format(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z";
    }

    public String getInquiryId() {
        return inquiryId;
    }

    public String getInquiryType() {
        return inquiryType;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getReplyEmail() {
        return replyEmail;
    }

    public String getStatus() {
        return status;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
