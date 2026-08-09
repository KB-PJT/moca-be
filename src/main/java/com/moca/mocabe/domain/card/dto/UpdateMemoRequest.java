package com.moca.mocabe.domain.card.dto;

import javax.validation.constraints.Size;

/** 보유 카드 메모 수정 요청이다. */
public class UpdateMemoRequest {

    @Size(max = 500, message = "메모는 500자 이하여야 합니다.")
    private String memo;

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }
}
