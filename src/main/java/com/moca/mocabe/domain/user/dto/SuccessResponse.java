package com.moca.mocabe.domain.user.dto;

/** 상태 변경 완료 여부만 필요한 API의 응답이다. */
public class SuccessResponse {

    private final boolean success;

    public SuccessResponse(boolean success) {
        this.success = success;
    }

    public boolean isSuccess() {
        return success;
    }
}
