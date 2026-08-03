package com.moca.mocabe.domain.codef.dto;

import javax.validation.constraints.NotBlank;

/** CODEF 카드 연동(Connected ID 생성) 요청이다. */
public class CreateCardLinkRequest {

    @NotBlank(message = "기관코드는 필수입니다.")
    private String institutionCode;

    private String id;

    private String password;

    private String cardNo;

    private String cardPassword;

    private String birthDate;

    public String getInstitutionCode() {
        return institutionCode;
    }

    public void setInstitutionCode(String institutionCode) {
        this.institutionCode = institutionCode;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

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

    public String getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(String birthDate) {
        this.birthDate = birthDate;
    }

    @Override
    public String toString() {
        return "CreateCardLinkRequest{institutionCode=" + institutionCode + ", id=[MASKED], "
                + "password=[MASKED], cardNo=[MASKED], cardPassword=[MASKED], "
                + "birthDate=[MASKED]}";
    }
}
