package com.moca.mocabe.domain.user.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/** 닉네임 변경 요청이다. */
public class UpdateNicknameRequest {

    @NotBlank(message = "닉네임은 비어 있을 수 없습니다.")
    @Size(max = 50, message = "닉네임은 50자 이하여야 합니다.")
    private String nickname;

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}
