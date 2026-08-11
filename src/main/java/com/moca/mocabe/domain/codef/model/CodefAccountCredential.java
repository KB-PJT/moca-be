package com.moca.mocabe.domain.codef.model;

/** codef_account_credentials 테이블의 CODEF 연동·암호화 자격정보를 나타내는 MyBatis 모델이다. */
public class CodefAccountCredential {

    private String codefAccountCredentialId;
    private String userId;
    private String issuerId;
    private String connectedId;
    private byte[] accountIdEnc;
    private byte[] accountPasswordEnc;
    private byte[] birthDateEnc;
    private byte[] pendingCardNumberEnc;
    private byte[] pendingCardPasswordEnc;
    private String credentialIdentityHash;
    private String status;

    public String getCodefAccountCredentialId() {
        return codefAccountCredentialId;
    }

    public void setCodefAccountCredentialId(String codefAccountCredentialId) {
        this.codefAccountCredentialId = codefAccountCredentialId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getIssuerId() {
        return issuerId;
    }

    public void setIssuerId(String issuerId) {
        this.issuerId = issuerId;
    }

    public String getConnectedId() {
        return connectedId;
    }

    public void setConnectedId(String connectedId) {
        this.connectedId = connectedId;
    }

    public byte[] getAccountIdEnc() {
        return accountIdEnc;
    }

    public void setAccountIdEnc(byte[] accountIdEnc) {
        this.accountIdEnc = accountIdEnc;
    }

    public byte[] getAccountPasswordEnc() {
        return accountPasswordEnc;
    }

    public void setAccountPasswordEnc(byte[] accountPasswordEnc) {
        this.accountPasswordEnc = accountPasswordEnc;
    }

    public byte[] getBirthDateEnc() {
        return birthDateEnc;
    }

    public void setBirthDateEnc(byte[] birthDateEnc) {
        this.birthDateEnc = birthDateEnc;
    }

    public byte[] getPendingCardNumberEnc() {
        return pendingCardNumberEnc;
    }

    public void setPendingCardNumberEnc(byte[] pendingCardNumberEnc) {
        this.pendingCardNumberEnc = pendingCardNumberEnc;
    }

    public byte[] getPendingCardPasswordEnc() {
        return pendingCardPasswordEnc;
    }

    public void setPendingCardPasswordEnc(byte[] pendingCardPasswordEnc) {
        this.pendingCardPasswordEnc = pendingCardPasswordEnc;
    }

    public String getCredentialIdentityHash() {
        return credentialIdentityHash;
    }

    public void setCredentialIdentityHash(String credentialIdentityHash) {
        this.credentialIdentityHash = credentialIdentityHash;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
