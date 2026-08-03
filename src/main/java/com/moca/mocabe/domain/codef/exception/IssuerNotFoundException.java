package com.moca.mocabe.domain.codef.exception;

/** 요청한 발급사가 issuers 기준정보에 존재하지 않을 때 발생한다. */
public class IssuerNotFoundException extends RuntimeException {

    public IssuerNotFoundException(String institutionCode) {
        super("등록되지 않은 발급사입니다: " + institutionCode);
    }
}
