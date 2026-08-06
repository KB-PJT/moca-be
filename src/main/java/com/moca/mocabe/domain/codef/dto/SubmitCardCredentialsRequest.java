package com.moca.mocabe.domain.codef.dto;

import javax.validation.constraints.NotBlank;

/**
 * 카드번호가 필요한 카드사에서, 계정 생성 시 입력한 카드가 아닌 다른 보유카드를 활성화하기 위해
 * 그 카드의 카드번호/비밀번호를 추가로 입력하는 요청이다. cardPassword는 카드사 정책상
 * 필요할 때만(requiresCardPassword) 필수이므로 서비스 계층에서 검증한다.
 */
public class SubmitCardCredentialsRequest {

    @NotBlank(message = "카드번호는 필수입니다.")
    private String cardNo;

    private String cardPassword;

    public String getCardNo() {
        return cardNo;
    }

    public void setCardNo(String cardNo) {
        this.cardNo = cardNo;
    }

    public String getCardPassword() {
        return cardPassword;
    }

    public void setCardPassword(String cardPassword) {
        this.cardPassword = cardPassword;
    }

    @Override
    public String toString() {
        return "SubmitCardCredentialsRequest{cardNo=[MASKED], cardPassword=[MASKED]}";
    }
}
