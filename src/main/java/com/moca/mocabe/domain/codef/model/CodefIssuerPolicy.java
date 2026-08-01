package com.moca.mocabe.domain.codef.model;

/** issuers 테이블의 CODEF 기관코드와 카드사별 연동 필수정보 정책을 담는 조회 모델이다. */
public class CodefIssuerPolicy {

    private String issuerId;
    private String institutionCode;
    private boolean requiresId;
    private boolean requiresPassword;
    private boolean requiresCardNo;
    private boolean requiresCardPassword;
    private boolean requiresBirthDate;

    public String getIssuerId() {
        return issuerId;
    }

    public void setIssuerId(String issuerId) {
        this.issuerId = issuerId;
    }

    public String getInstitutionCode() {
        return institutionCode;
    }

    public void setInstitutionCode(String institutionCode) {
        this.institutionCode = institutionCode;
    }

    public boolean isRequiresId() {
        return requiresId;
    }

    public void setRequiresId(boolean requiresId) {
        this.requiresId = requiresId;
    }

    public boolean isRequiresPassword() {
        return requiresPassword;
    }

    public void setRequiresPassword(boolean requiresPassword) {
        this.requiresPassword = requiresPassword;
    }

    public boolean isRequiresCardNo() {
        return requiresCardNo;
    }

    public void setRequiresCardNo(boolean requiresCardNo) {
        this.requiresCardNo = requiresCardNo;
    }

    public boolean isRequiresCardPassword() {
        return requiresCardPassword;
    }

    public void setRequiresCardPassword(boolean requiresCardPassword) {
        this.requiresCardPassword = requiresCardPassword;
    }

    public boolean isRequiresBirthDate() {
        return requiresBirthDate;
    }

    public void setRequiresBirthDate(boolean requiresBirthDate) {
        this.requiresBirthDate = requiresBirthDate;
    }
}
