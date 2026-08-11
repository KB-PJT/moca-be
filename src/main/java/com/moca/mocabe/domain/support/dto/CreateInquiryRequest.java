package com.moca.mocabe.domain.support.dto;

import com.moca.mocabe.global.validation.MaxCodePoints;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

/** 문의 등록 요청이다. */
public class CreateInquiryRequest {

    @NotBlank(message = "문의 유형은 필수입니다.")
    @Pattern(regexp = "card_link|performance_benefit|map_merchant|account_login|bug|etc",
            message = "문의 유형이 올바르지 않습니다.")
    private String inquiryType;

    @NotBlank(message = "제목은 필수입니다.")
    @MaxCodePoints(value = 100, message = "제목은 100자 이하여야 합니다.")
    private String title;

    @NotBlank(message = "내용은 필수입니다.")
    @MaxCodePoints(value = 2000, message = "내용은 2000자 이하여야 합니다.")
    private String content;

    @NotBlank(message = "답변받을 이메일은 필수입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @MaxCodePoints(value = 255, message = "이메일은 255자 이하여야 합니다.")
    private String replyEmail;

    public String getInquiryType() {
        return inquiryType;
    }

    public void setInquiryType(String inquiryType) {
        this.inquiryType = inquiryType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getReplyEmail() {
        return replyEmail;
    }

    public void setReplyEmail(String replyEmail) {
        this.replyEmail = replyEmail;
    }
}
