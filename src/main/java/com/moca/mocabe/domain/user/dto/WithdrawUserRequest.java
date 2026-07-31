package com.moca.mocabe.domain.user.dto;

import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/** 탈퇴 사유 및 확인 요청이다. */
public class WithdrawUserRequest {

    @Pattern(regexp = "inconvenient|not_needed|incorrect_benefit|privacy_concern|low_usage|etc",
            message = "탈퇴 사유가 올바르지 않습니다.")
    private String reason;

    @Size(max = 500, message = "기타 사유는 500자 이하여야 합니다.")
    private String reasonDetail;

    @AssertTrue(message = "탈퇴 안내 확인이 필요합니다.")
    private boolean confirmed;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getReasonDetail() {
        return reasonDetail;
    }

    public void setReasonDetail(String reasonDetail) {
        this.reasonDetail = reasonDetail;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }
}
